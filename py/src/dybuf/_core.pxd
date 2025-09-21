ctypedef unsigned int uint
ctypedef unsigned long long uint64
ctypedef long long int64
ctypedef unsigned int uint32
ctypedef unsigned short uint16
ctypedef unsigned char uint8
ctypedef unsigned char boolean
ctypedef unsigned char byte

cdef extern from "dybuf.h":
    ctypedef struct dybuf:
        byte* _data
        uint _capacity
        uint _limit
        uint _position
        uint _mark
        boolean _fixedCapacity
        boolean _should_release_instance
        boolean _should_release_data

    dybuf* dyb_create(dybuf* dyb, uint capacity)
    dybuf* dyb_copy(dybuf* dyb, byte* data, uint capacity, boolean no_copy)
    dybuf* dyb_refer(dybuf* dyb, byte* data, uint capacity, boolean for_write)
    void dyb_release(dybuf* dyb)

    uint dyb_get_capacity(dybuf* dyb)
    dybuf* dyb_set_capacity(dybuf* dyb, uint newCapacity)
    uint dyb_get_position(dybuf* dyb)
    dybuf* dyb_set_position(dybuf* dyb, uint newPosition)
    uint dyb_get_limit(dybuf* dyb)
    dybuf* dyb_set_limit(dybuf* dyb, uint newLimit)
    dybuf* dyb_compact(dybuf* dyb)
    uint dyb_get_remainder(dybuf* dyb)

    dybuf* dyb_mark(dybuf* dyb)
    dybuf* dyb_reset(dybuf* dyb)
    dybuf* dyb_clear(dybuf* dyb)
    dybuf* dyb_flip(dybuf* dyb)
    dybuf* dyb_rewind(dybuf* dyb)

    boolean dyb_next_bool(dybuf* dyb)
    boolean dyb_peek_bool(dybuf* dyb)
    uint8 dyb_next_u8(dybuf* dyb)
    uint8 dyb_peek_u8(dybuf* dyb)
    uint16 dyb_next_u16(dybuf* dyb)
    uint16 dyb_peek_u16(dybuf* dyb)
    uint32 dyb_next_u24(dybuf* dyb)
    uint32 dyb_peek_u24(dybuf* dyb)
    uint32 dyb_next_u32(dybuf* dyb)
    uint32 dyb_peek_u32(dybuf* dyb)
    uint64 dyb_next_u40(dybuf* dyb)
    uint64 dyb_peek_u40(dybuf* dyb)
    uint64 dyb_next_u48(dybuf* dyb)
    uint64 dyb_peek_u48(dybuf* dyb)
    uint64 dyb_next_u56(dybuf* dyb)
    uint64 dyb_peek_u56(dybuf* dyb)
    uint64 dyb_next_u64(dybuf* dyb)
    uint64 dyb_peek_u64(dybuf* dyb)

    dybuf* dyb_append_bool(dybuf* dyb, boolean value)
    dybuf* dyb_append_u8(dybuf* dyb, uint8 value)
    dybuf* dyb_append_u16(dybuf* dyb, uint16 value)
    dybuf* dyb_append_u24(dybuf* dyb, uint32 value)
    dybuf* dyb_append_u32(dybuf* dyb, uint32 value)
    dybuf* dyb_append_u40(dybuf* dyb, uint64 value)
    dybuf* dyb_append_u48(dybuf* dyb, uint64 value)
    dybuf* dyb_append_u56(dybuf* dyb, uint64 value)
    dybuf* dyb_append_u64(dybuf* dyb, uint64 value)

    dybuf* dyb_append_var_u64(dybuf* dyb, uint64 value)
    uint64 dyb_next_var_u64(dybuf* dyb)
    dybuf* dyb_append_var_s64(dybuf* dyb, int64 value)
    int64 dyb_next_var_s64(dybuf* dyb)

    dybuf* dyb_append_data_with_var_len(dybuf* dyb, uint8* data, uint size)
    uint8* dyb_next_data_with_var_len(dybuf* dyb, uint* size)

    uint8* dyb_next_data_without_len(dybuf* dyb, uint len)
    dybuf* dyb_append_data_without_len(dybuf* dyb, uint8* data, uint length)

    cdef const unsigned int typdex_typ_none
    cdef const unsigned int typdex_typ_bool
    cdef const unsigned int typdex_typ_int
    cdef const unsigned int typdex_typ_uint
    cdef const unsigned int typdex_typ_float
    cdef const unsigned int typdex_typ_double
    cdef const unsigned int typdex_typ_string
    cdef const unsigned int typdex_typ_bytes
    cdef const unsigned int typdex_typ_array
    cdef const unsigned int typdex_typ_map
    cdef const unsigned int typdex_typ_f

    dybuf* dyb_append_typdex(dybuf* dyb, uint8 type, uint index)
    void dyb_next_typdex(dybuf* dyb, uint8* type, uint* index)
    void dyb_peek_typdex(dybuf* dyb, uint8* type, uint* index)
