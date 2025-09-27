# cython: language_level=3, boundscheck=False, wraparound=False

import warnings

from cpython.bytes cimport PyBytes_FromStringAndSize

cdef inline void _ensure_success(dybuf* result):
    if result == NULL:
        raise MemoryError("dybuf operation failed")

cdef inline void _ensure_readable(dybuf* buffer, unsigned int amount):
    if dyb_get_remainder(buffer) < amount:
        raise EOFError("not enough data in buffer")

cdef inline unsigned int _required_typdex_length(uint8 header):
    if (header & 0x80) == 0:
        return 1
    elif (header & 0x40) == 0:
        return 2
    elif (header & 0x20) == 0:
        return 3
    elif (header & 0x10) == 0:
        return 4
    return 0

cdef class DyBuf:
    cdef dybuf* _buffer
    cdef object _keep_alive

    def __cinit__(self, unsigned int capacity=0, object data=None, *, bint copy=True, object writable=None):
        cdef bint is_writable
        cdef const unsigned char* ptr = NULL
        cdef const unsigned char[::1] mv_view
        self._buffer = NULL
        self._keep_alive = None

        if data is not None:
            mv = memoryview(data).cast('B')
            mv_view = mv
            n = mv.nbytes
            if n:
                ptr = &mv_view[0]

            if writable is None:
                is_writable = False
            else:
                is_writable = bool(writable)

            if copy:
                self._buffer = dyb_copy(NULL, <byte*>ptr, <uint>n, 0)
            else:
                self._keep_alive = mv
                self._buffer = dyb_refer(NULL, <byte*>ptr, <uint>n, is_writable)

            if self._buffer == NULL:
                raise MemoryError("failed to initialize dybuf")
        else:
            if writable is None:
                is_writable = True
            else:
                is_writable = bool(writable)
            self._buffer = dyb_create(NULL, capacity)
            if self._buffer == NULL:
                raise MemoryError("failed to allocate dybuf")
            if not is_writable:
                _ensure_success(dyb_set_limit(self._buffer, 0))

    def __dealloc__(self):
        if self._buffer != NULL:
            dyb_release(self._buffer)
            self._buffer = NULL
        self._keep_alive = None

    property capacity:
        def __get__(self):
            return dyb_get_capacity(self._buffer)

    property limit:
        def __get__(self):
            return dyb_get_limit(self._buffer)

        def __set__(self, unsigned int new_limit):
            _ensure_success(dyb_set_limit(self._buffer, new_limit))

    property position:
        def __get__(self):
            return dyb_get_position(self._buffer)

        def __set__(self, unsigned int new_position):
            _ensure_success(dyb_set_position(self._buffer, new_position))

    def remaining(self):
        return dyb_get_remainder(self._buffer)

    def clear(self):
        dyb_clear(self._buffer)

    def flip(self):
        dyb_flip(self._buffer)

    def rewind(self):
        dyb_rewind(self._buffer)

    def compact(self):
        _ensure_success(dyb_compact(self._buffer))

    def mark(self):
        dyb_mark(self._buffer)

    def reset(self):
        dyb_reset(self._buffer)

    def ensure_capacity(self, unsigned int new_capacity):
        _ensure_success(dyb_set_capacity(self._buffer, new_capacity))
        return self

    def to_bytes(self):
        cdef unsigned int length = dyb_get_limit(self._buffer)
        return PyBytes_FromStringAndSize(<char*>self._buffer._data, length)

    def write(self, data):
        cdef const unsigned char* ptr = NULL
        cdef const unsigned char[::1] mv_view
        mv = memoryview(data).cast('B')
        mv_view = mv
        n = mv.nbytes
        if n:
            ptr = &mv_view[0]
        _ensure_success(dyb_append_data_without_len(self._buffer, <uint8*>ptr, <uint>n))
        return self

    def read(self, size=None):
        cdef unsigned int amount
        cdef uint8* ptr = NULL
        if size is None:
            amount = dyb_get_remainder(self._buffer)
        else:
            if size < 0:
                raise ValueError("size must be non-negative")
            amount = <unsigned int>size
        if amount == 0:
            return b''
        _ensure_readable(self._buffer, amount)
        ptr = dyb_next_data_without_len(self._buffer, amount)
        if ptr == NULL:
            raise EOFError("not enough data in buffer")
        return PyBytes_FromStringAndSize(<char*>ptr, amount)

    def append_bool(self, bint value):
        _ensure_success(dyb_append_bool(self._buffer, value))
        return self

    def append_uint8(self, unsigned int value):
        if value > 0xFF:
            raise ValueError("value out of range for uint8")
        _ensure_success(dyb_append_u8(self._buffer, <uint8>value))
        return self

    def append_uint16(self, unsigned int value):
        if value > 0xFFFF:
            raise ValueError("value out of range for uint16")
        _ensure_success(dyb_append_u16(self._buffer, <uint16>value))
        return self

    def append_uint24(self, unsigned int value):
        if value > 0xFFFFFF:
            raise ValueError("value out of range for uint24")
        _ensure_success(dyb_append_u24(self._buffer, <uint32>value))
        return self

    def append_uint32(self, unsigned int value):
        _ensure_success(dyb_append_u32(self._buffer, <uint32>value))
        return self

    def append_uint40(self, unsigned long long value):
        if value > ((1 << 40) - 1):
            raise ValueError("value out of range for uint40")
        _ensure_success(dyb_append_u40(self._buffer, <uint64>value))
        return self

    def append_uint48(self, unsigned long long value):
        if value > ((1 << 48) - 1):
            raise ValueError("value out of range for uint48")
        _ensure_success(dyb_append_u48(self._buffer, <uint64>value))
        return self

    def append_uint56(self, unsigned long long value):
        if value > ((1 << 56) - 1):
            raise ValueError("value out of range for uint56")
        _ensure_success(dyb_append_u56(self._buffer, <uint64>value))
        return self

    def append_uint64(self, unsigned long long value):
        _ensure_success(dyb_append_u64(self._buffer, <uint64>value))
        return self

    def append_var_u64(self, unsigned long long value):
        _ensure_success(dyb_append_var_u64(self._buffer, <uint64>value))
        return self

    def append_var_uint(self, unsigned long long value):
        warnings.warn(
            "append_var_uint is deprecated; use append_var_u64 instead",
            DeprecationWarning,
            stacklevel=2,
        )
        return self.append_var_u64(value)

    def append_var_s64(self, long long value):
        _ensure_success(dyb_append_var_s64(self._buffer, <int64>value))
        return self

    def append_var_int(self, long long value):
        warnings.warn(
            "append_var_int is deprecated; use append_var_s64 instead",
            DeprecationWarning,
            stacklevel=2,
        )
        return self.append_var_s64(value)

    def append_var_bytes(self, object data):
        cdef const unsigned char[::1] mv_view
        cdef const unsigned char* ptr = NULL
        cdef Py_ssize_t length

        mv = memoryview(data).cast('B')
        mv_view = mv
        length = mv.nbytes
        if length:
            ptr = &mv_view[0]
        _ensure_success(dyb_append_data_with_var_len(self._buffer, <uint8*>ptr, <uint>length))
        return self

    def append_var_string(self, str text, encoding="utf-8"):
        cdef bytes encoded = text.encode(encoding)
        self.append_var_bytes(encoded)
        return self

    def append_typdex(self, unsigned int type, unsigned int index):
        if type > 0xFF:
            raise ValueError("typdex type must fit in 8 bits")
        if dyb_append_typdex(self._buffer, <uint8>type, <uint>index) == NULL:
            raise ValueError("typdex index is out of supported range")
        return self

    cdef void _ensure_typdex_available(self):
        cdef uint remaining = dyb_get_remainder(self._buffer)
        cdef uint8 header
        cdef unsigned int needed
        if remaining == 0:
            raise EOFError("not enough data in buffer")
        header = dyb_peek_u8(self._buffer)
        needed = _required_typdex_length(header)
        if needed == 0:
            raise ValueError("invalid typdex header")
        if remaining < needed:
            raise EOFError("not enough data in buffer")

    def next_typdex(self):
        cdef uint8 typ = 0
        cdef uint index = 0
        self._ensure_typdex_available()
        dyb_next_typdex(self._buffer, &typ, &index)
        return typ, index

    def peek_typdex(self):
        cdef uint8 typ = 0
        cdef uint index = 0
        self._ensure_typdex_available()
        dyb_peek_typdex(self._buffer, &typ, &index)
        return typ, index

    def next_bool(self):
        _ensure_readable(self._buffer, 1)
        return bool(dyb_next_bool(self._buffer))

    def peek_bool(self):
        _ensure_readable(self._buffer, 1)
        return bool(dyb_peek_bool(self._buffer))

    def next_uint8(self):
        _ensure_readable(self._buffer, 1)
        return dyb_next_u8(self._buffer)

    def peek_uint8(self):
        _ensure_readable(self._buffer, 1)
        return dyb_peek_u8(self._buffer)

    def next_uint16(self):
        _ensure_readable(self._buffer, 2)
        return dyb_next_u16(self._buffer)

    def peek_uint16(self):
        _ensure_readable(self._buffer, 2)
        return dyb_peek_u16(self._buffer)

    def next_uint24(self):
        _ensure_readable(self._buffer, 3)
        return dyb_next_u24(self._buffer)

    def peek_uint24(self):
        _ensure_readable(self._buffer, 3)
        return dyb_peek_u24(self._buffer)

    def next_uint32(self):
        _ensure_readable(self._buffer, 4)
        return dyb_next_u32(self._buffer)

    def peek_uint32(self):
        _ensure_readable(self._buffer, 4)
        return dyb_peek_u32(self._buffer)

    def next_uint40(self):
        _ensure_readable(self._buffer, 5)
        return dyb_next_u40(self._buffer)

    def peek_uint40(self):
        _ensure_readable(self._buffer, 5)
        return dyb_peek_u40(self._buffer)

    def next_uint48(self):
        _ensure_readable(self._buffer, 6)
        return dyb_next_u48(self._buffer)

    def peek_uint48(self):
        _ensure_readable(self._buffer, 6)
        return dyb_peek_u48(self._buffer)

    def next_uint56(self):
        _ensure_readable(self._buffer, 7)
        return dyb_next_u56(self._buffer)

    def peek_uint56(self):
        _ensure_readable(self._buffer, 7)
        return dyb_peek_u56(self._buffer)

    def next_uint64(self):
        _ensure_readable(self._buffer, 8)
        return dyb_next_u64(self._buffer)

    def peek_uint64(self):
        _ensure_readable(self._buffer, 8)
        return dyb_peek_u64(self._buffer)

    def next_var_u64(self):
        return dyb_next_var_u64(self._buffer)

    def next_var_uint(self):
        warnings.warn(
            "next_var_uint is deprecated; use next_var_u64 instead",
            DeprecationWarning,
            stacklevel=2,
        )
        return self.next_var_u64()

    def next_var_s64(self):
        return dyb_next_var_s64(self._buffer)

    def next_var_int(self):
        warnings.warn(
            "next_var_int is deprecated; use next_var_s64 instead",
            DeprecationWarning,
            stacklevel=2,
        )
        return self.next_var_s64()

    def next_var_bytes(self):
        cdef uint size = 0
        cdef uint8* ptr
        ptr = dyb_next_data_with_var_len(self._buffer, &size)
        if ptr == NULL and size != 0:
            raise EOFError("not enough data in buffer")
        return PyBytes_FromStringAndSize(<char*>ptr, size)

    def next_var_string(self, encoding="utf-8"):
        cdef bytes data = self.next_var_bytes()
        return data.decode(encoding)

    def __len__(self):
        return dyb_get_limit(self._buffer)

    def __repr__(self):
        return f"DyBuf(capacity={dyb_get_capacity(self._buffer)}, limit={dyb_get_limit(self._buffer)}, position={dyb_get_position(self._buffer)})"

TYPDEX_TYP_NONE = typdex_typ_none
TYPDEX_TYP_BOOL = typdex_typ_bool
TYPDEX_TYP_INT = typdex_typ_int
TYPDEX_TYP_UINT = typdex_typ_uint
TYPDEX_TYP_FLOAT = typdex_typ_float
TYPDEX_TYP_DOUBLE = typdex_typ_double
TYPDEX_TYP_STRING = typdex_typ_string
TYPDEX_TYP_BYTES = typdex_typ_bytes
TYPDEX_TYP_ARRAY = typdex_typ_array
TYPDEX_TYP_MAP = typdex_typ_map
TYPDEX_TYP_F = typdex_typ_f
