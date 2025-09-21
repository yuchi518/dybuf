DyBuf API Reference
===================

.. module:: dybuf

This document describes the ``DyBuf`` class exposed by the :mod:`dybuf` package.
``DyBuf`` is a thin Cython wrapper around the C ``dybuf`` dynamic buffer library.


Class Constructor
-----------------

.. class:: DyBuf(capacity=0, data=None, *, copy=True, writable=None)

   Create a buffer either by allocating new storage or by wrapping existing
   binary data.

   :param int capacity: Initial capacity when allocating a new buffer. Ignored
       if ``data`` is provided.
   :param bytes-like data: Optional source of initial contents. Any object
       accepted by :class:`memoryview` (``bytes``, ``bytearray``, ``array.array``,
       etc.) is supported.
   :param bool copy: When ``True`` (default) the contents of ``data`` are copied
       into private storage. When ``False`` the buffer references the original
       memory. Ignored when ``data`` is ``None``.
   :param bool writable: Controls whether the buffer can be written after
       construction:

       * When ``data`` is provided and ``copy`` is ``False`` this decides
         whether modifications propagate back to the source object. Defaults to
         ``False``.
       * When ``data`` is ``None`` this decides whether the freshly allocated
         buffer is writable. Defaults to ``True``.

   :raises MemoryError: If allocation of the underlying ``dybuf`` structure
       fails.


Buffer Properties
-----------------

.. attribute:: DyBuf.capacity

   Read-only integer capacity of the underlying storage.

.. attribute:: DyBuf.limit

   Current logical length of the buffer. Assigning to ``limit`` truncates or
   extends the buffer (potentially reallocating) and clamps ``position`` and
   ``mark`` to the new limit.

.. attribute:: DyBuf.position

   Read/write cursor used by the ``next_*`` and ``read`` family of methods.


Core Methods
------------

All methods mutate and/or read from the buffer unless stated otherwise.

.. method:: DyBuf.remaining()

   Return the number of unread bytes between ``position`` and ``limit``.

.. method:: DyBuf.clear()

   Reset ``position`` to ``0`` and ``limit`` to ``capacity`` (write mode).

.. method:: DyBuf.flip()

   Prepare the buffer for reading after writing by setting ``limit`` to the
   current ``position`` and rewinding ``position`` to ``0``.

.. method:: DyBuf.rewind()

   Set ``position`` to ``0`` while leaving ``limit`` unchanged.

.. method:: DyBuf.compact()

   Shift unread data to the beginning of the buffer, shrinking ``limit`` to the
   unread length and resetting ``position`` and ``mark`` to ``0``.

.. method:: DyBuf.mark()

   Remember the current ``position``.

.. method:: DyBuf.reset()

   Restore ``position`` to the last value recorded by :meth:`mark`.

.. method:: DyBuf.ensure_capacity(new_capacity)

   Grow the underlying storage to at least ``new_capacity`` bytes. Raises
   :class:`MemoryError` on allocation failure.

.. method:: DyBuf.to_bytes()

   Return the first ``limit`` bytes of the buffer as a new :class:`bytes`
   object.

.. method:: DyBuf.write(data)

   Append raw ``data`` (any bytes-like object) at the current ``position``.
   Automatically advances ``position`` and grows ``limit`` as required.

.. method:: DyBuf.read(size=None)

   Read ``size`` bytes (or all remaining bytes when ``size`` is ``None``) from
   the buffer, advancing ``position``. Raises :class:`EOFError` when not enough
   data is available and :class:`ValueError` if ``size`` is negative.


Integer Append Methods
----------------------

All append methods advance the write cursor and grow ``limit`` if necessary.

.. method:: DyBuf.append_bool(value)

   Append ``value`` encoded as a single byte.

.. method:: DyBuf.append_uint8(value)
.. method:: DyBuf.append_uint16(value)
.. method:: DyBuf.append_uint24(value)
.. method:: DyBuf.append_uint32(value)
.. method:: DyBuf.append_uint40(value)
.. method:: DyBuf.append_uint48(value)
.. method:: DyBuf.append_uint56(value)
.. method:: DyBuf.append_uint64(value)
.. method:: DyBuf.append_var_uint(value)
.. method:: DyBuf.append_var_int(value)

   Append unsigned integers using big-endian network byte order. Each method
   validates that ``value`` fits the declared bit-width and raises
   :class:`ValueError` otherwise.

   ``append_var_uint`` and ``append_var_int`` use the library's variable-length
   encoding (similar to protobuf varints) to store unsigned or zig-zag encoded
   signed 64-bit integers respectively.


Variable-Length Data Methods
----------------------------

These helpers serialize arbitrary byte buffers or encoded strings with a
varint-prefixed length.

.. method:: DyBuf.append_var_bytes(data)
.. method:: DyBuf.append_var_string(text, encoding='utf-8')

   ``append_var_bytes`` accepts any bytes-like object and prefixes it with a
   variable-length size. ``append_var_string`` first encodes the Python string
   using the specified encoding (default UTF-8) and writes the resulting bytes
   with the same length-prefixed format. Any codec supported by
   :py:meth:`str.encode` can be used.


Integer Read/Peek Methods
-------------------------

The ``next_*`` methods consume bytes while the ``peek_*`` counterparts leave
``position`` unchanged.

.. method:: DyBuf.next_bool()
.. method:: DyBuf.peek_bool()
.. method:: DyBuf.next_uint8()
.. method:: DyBuf.peek_uint8()
.. method:: DyBuf.next_uint16()
.. method:: DyBuf.peek_uint16()
.. method:: DyBuf.next_uint24()
.. method:: DyBuf.peek_uint24()
.. method:: DyBuf.next_uint32()
.. method:: DyBuf.peek_uint32()
.. method:: DyBuf.next_uint40()
.. method:: DyBuf.peek_uint40()
.. method:: DyBuf.next_uint48()
.. method:: DyBuf.peek_uint48()
.. method:: DyBuf.next_uint56()
.. method:: DyBuf.peek_uint56()
.. method:: DyBuf.next_uint64()
.. method:: DyBuf.peek_uint64()
.. method:: DyBuf.next_var_uint()
.. method:: DyBuf.next_var_int()

   Raise :class:`EOFError` if insufficient bytes remain in the buffer.

.. method:: DyBuf.next_var_bytes()
.. method:: DyBuf.next_var_string(encoding='utf-8')

   Read data written with :meth:`append_var_bytes` or
   :meth:`append_var_string`. ``next_var_string`` decodes the retrieved bytes
   using the provided encoding (default UTF-8).


Typdex Helpers
--------------

``DyBuf`` can emit and inspect **typdex** markers—compact encodings of a logical
type identifier paired with an index. These are typically used by ``dypkt`` and
related protocols to describe schema metadata or function identifiers.

.. method:: DyBuf.append_typdex(type, index)

   Append a typdex marker. ``type`` must fit within 8 bits. The method selects
   the smallest encoding that can represent ``index`` and raises
   :class:`ValueError` if no encoding is available.

.. method:: DyBuf.peek_typdex()
.. method:: DyBuf.next_typdex()

   Inspect or consume the next typdex marker, returning a ``(type, index)``
   tuple. Both helpers raise :class:`EOFError` when insufficient bytes remain
   and :class:`ValueError` if the header bits do not describe a valid typdex
   encoding.


Typdex Constants
----------------

The module re-exports the canonical typdex type identifiers from the C library
for convenience:

.. data:: TYPDEX_TYP_NONE
.. data:: TYPDEX_TYP_BOOL
.. data:: TYPDEX_TYP_INT
.. data:: TYPDEX_TYP_UINT
.. data:: TYPDEX_TYP_FLOAT
.. data:: TYPDEX_TYP_DOUBLE
.. data:: TYPDEX_TYP_STRING
.. data:: TYPDEX_TYP_BYTES
.. data:: TYPDEX_TYP_ARRAY
.. data:: TYPDEX_TYP_MAP
.. data:: TYPDEX_TYP_F

These constants can be passed to :meth:`DyBuf.append_typdex` (or compared with
the values returned by :meth:`DyBuf.peek_typdex`) to avoid hard-coding numeric
literals.


Python Special Methods
----------------------

.. method:: DyBuf.__len__()

   Return ``limit`` so ``len(buf)`` reports the logical size of the buffer.

.. method:: DyBuf.__repr__()

   Provide a concise string representation including ``capacity``, ``limit``
   and ``position``.
