from collections import deque
from util.netutils import get_ip_addr

import logging
import s3p
import socket
import select
import threading

__author__ = "Nick Pascucci (npascut1@gmail.com)"

_LOG = logging.getLogger(__name__)

S3P_START = chr(0x5B)
S3P_TERM = chr(0x5D)
TIMEOUT = 1


class AsyncS3PServerSocket(object):
    """An asynchronous socket that reads S3P packets at the given port. Will spawn another thread to
    handle socket IO."""
    def __init__(self, port):
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        _LOG.debug("Binding to {}:{}".format(get_ip_addr(), port))
        self.sock.bind((get_ip_addr(), port))
        self.conn = None
        self.addr = None
        self.lock = threading.Lock()
        self.send_lock = threading.Lock()
        self.unparsed_data = []
        self.inbound_packets = deque()
        self.outbound_packets = deque()
        self.shutdown_event = threading.Event()
        self.thread = AsyncS3PSocketWorker(self)
        _LOG.debug("AsyncS3PSocket setup complete")

    def start(self):
        """Start the attached thread to begin using the socket."""
        self.sock.listen(5)
        self.thread.start()
        _LOG.debug("AsyncS3PSocket thread started")

    def stop(self):
        """Shut down the socket."""
        self.shutdown_event.set()

        self.thread.join(TIMEOUT)
        if self.thread.isAlive():
            self.sock.close()
            _LOG.warning("AsyncS3PSocket thread still alive after shutdown call!")
        if self.conn:
            try:
                self.conn.shutdown(socket.SHUT_RDWR)
                self.conn.close()
            except socket.error:
                pass
        _LOG.debug("AsyncS3PSocket thread stopped")

    def next(self):
        """Remove and return the next packet in the inbound queue."""
        with self.lock:
            try:
                packet = self.inbound_packets.popleft()
            except IndexError:
                packet = None
        return packet

    def size(self):
        """Read the number of packets in the inbound queue."""
        with self.lock:
            size = self.inbound_packets.size()
        return size

    def send(self, packet_data):
        """Send data over the socket. Encoding will be performed in the same thread, and may raise
        ValueError if the data cannot be encoded."""
        s3p_packet = s3p.build(packet_data)
        with self.send_lock:
            self.outbound_packets.append(s3p_packet)
            _LOG.debug("Packet enqueued for send: {} ({} bytes)".format(s3p_packet, len(s3p_packet)))

    def _add_packet(self, packet):
        with self.lock:
            self.inbound_packets.append(packet)

class AsyncS3PSocketWorker(threading.Thread):
    def __init__(self, async_sock, timeout=1.0):
        super(AsyncS3PSocketWorker, self).__init__()
        self.asock = async_sock
        self.timeout = timeout

    def run(self):
        self._poll_and_update()

    def _establish_connection(self):
        """Wait for incoming connections and accept them."""
        while True:
            if self.asock.shutdown_event.isSet(): 
                return
            readable, writable, errored = select.select([self.asock.sock], [], [], 1)
            _LOG.debug("select returned with: {} {} {}".format(readable, writable, errored))
            if readable:
                self.asock.conn, self.asock.addr = readable[0].accept()
                _LOG.debug("AsyncS3PSocket accepted connection from {}. Timeout: {}s"
                           .format(self.asock.addr, self.timeout))
                self.asock.conn.settimeout(self.timeout)
                return

    def _poll_and_update(self):
        """Poll the given socket and update its packet queue."""
        # TODO Refactor this into a method on the socket itself
        unparsed_data = ""
        while not self.asock.shutdown_event.isSet():
            close_when_done = False
            while not self.asock.conn:
                self._establish_connection()
                if self.asock.shutdown_event.isSet():
                    return

            try:
                _LOG.debug("Waiting for data")
                new_data = self.asock.conn.recv(1024)
                _LOG.debug("Received {} bytes: < {} >".format(len(new_data), new_data))
                unparsed_data += new_data
            except socket.timeout:
                close_when_done = True
                _LOG.debug("Socket timed out waiting for data, closing connection.")

            while unparsed_data:
                try:
                    _LOG.debug("Attempting to parse {} bytes of data: < {} >".format(len(unparsed_data), unparsed_data))
                    packet, length = self._read_next_packet(unparsed_data)
                    self.asock._add_packet(packet)
                    _LOG.debug("Added packet: {}".format(packet))
                    unparsed_data = unparsed_data[length:]
                except ValueError as ve:
                    _LOG.debug("No packet in data: {}".format(ve))
                    break
            with self.asock.send_lock:
                # TODO Split read and send into separate threads.
                for packet_num in range(len(self.asock.outbound_packets)):
                    packet = self.asock.outbound_packets.pop()
                    self.asock.conn.sendall(packet)
                    _LOG.debug("Sent packet {}".format(packet))
            if close_when_done:
                self.asock.conn.close()
                self.asock.conn = None

        if self.asock.shutdown_event.isSet():
            _LOG.debug("Received shutdown event, exiting.")

    # TODO This should really live in libS3P.
    def _read_next_packet(self, raw_data):
        """Read the next S3P socket in the given data. This operation is non-destructive: the original 
        data is untouched. The caller must remove the read bytes from the data source.

        Returns: 
            The packet's decoded data and the number of bytes consumed from the data.
        Raises:
            ValueError if the given data does not contain an S3P packet.
        """
        packet = ""
        start = 0
        started = False
        for idx, char in enumerate(raw_data):
            if not started and char == S3P_START:
                start = idx
                started = True
            elif char == S3P_TERM:
                packet = raw_data[start:idx+1]
                break
        _LOG.debug("Attempting to parse < {} > ({} bytes)".format(packet, len(packet)))
        return (s3p.read(packet), len(packet))
