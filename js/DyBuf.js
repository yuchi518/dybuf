export const TYPDEX_TYP_NONE = 0x00;
export const TYPDEX_TYP_BOOL = 0x01;
export const TYPDEX_TYP_INT = 0x02;
export const TYPDEX_TYP_UINT = 0x03;
export const TYPDEX_TYP_FLOAT = 0x06;
export const TYPDEX_TYP_DOUBLE = 0x07;
export const TYPDEX_TYP_STRING = 0x0A;
export const TYPDEX_TYP_BYTES = 0x0B;
export const TYPDEX_TYP_ARRAY = 0x0C;
export const TYPDEX_TYP_MAP = 0x0D;
export const TYPDEX_TYP_OBJ = 0x0E;
export const TYPDEX_TYP_F = 0x0F;

export const DYPE_F_EOF = 0;
export const DYPE_F_VERSION = 1;
export const DYPE_F_PROTOCOL = 7;
export const DYPE_F_PROTO_VERSION = 8;
export const JSON_DYBUF_FORMAT_VERSION = 1;

const HAS_NATIVE_BIGINT64 = typeof BigInt === 'function'
    && typeof DataView !== 'undefined'
    && DataView.prototype
    && typeof DataView.prototype.getBigUint64 === 'function'
    && typeof DataView.prototype.setBigUint64 === 'function';
if (!HAS_NATIVE_BIGINT64) {
    throw new Error('DyBuf requires native BigInt and DataView 64-bit support');
}

/*function pow2(n) {
    return (n >= 0 && n < 31) ? (1 << n) : (pow2[n] || (pow2[n] = Math.pow(2, n)));
}*/

export class DyBuf {
    static TYPDEX_TYP_NONE = TYPDEX_TYP_NONE;
    static TYPDEX_TYP_BOOL = TYPDEX_TYP_BOOL;
    static TYPDEX_TYP_INT = TYPDEX_TYP_INT;
    static TYPDEX_TYP_UINT = TYPDEX_TYP_UINT;
    static TYPDEX_TYP_FLOAT = TYPDEX_TYP_FLOAT;
    static TYPDEX_TYP_DOUBLE = TYPDEX_TYP_DOUBLE;
    static TYPDEX_TYP_STRING = TYPDEX_TYP_STRING;
    static TYPDEX_TYP_BYTES = TYPDEX_TYP_BYTES;
    static TYPDEX_TYP_ARRAY = TYPDEX_TYP_ARRAY;
    static TYPDEX_TYP_MAP = TYPDEX_TYP_MAP;
    static TYPDEX_TYP_OBJ = TYPDEX_TYP_OBJ;
    static TYPDEX_TYP_F = TYPDEX_TYP_F;
    static DYPE_F_EOF = DYPE_F_EOF;
    static DYPE_F_VERSION = DYPE_F_VERSION;
    static DYPE_F_PROTOCOL = DYPE_F_PROTOCOL;
    static DYPE_F_PROTO_VERSION = DYPE_F_PROTO_VERSION;
    static JSON_DYBUF_FORMAT_VERSION = JSON_DYBUF_FORMAT_VERSION;

    constructor(buff, copy_or_not=false) {
        if (typeof buff == "number") {
            // buffer size
            buff = new ArrayBuffer(buff);
            this._capacity = buff.byteLength;
            this._limit = 0;
            this._position = 0;
            this._mark = 0;
        } else {
            if (copy_or_not) {
                buff = buff.slice(0);
            } else {
                //buff = buff;
            }
            this._limit = this._capacity = buff.byteLength;
            this._position = 0;
            this._mark = 0;
        }

        this._data = new DataView(buff);
    }

    capacity() { return this._capacity; }

    setCapacity(newCapacity) {
        if (newCapacity === this._capacity) return this;

        if (newCapacity < 1024)
            newCapacity = Math.ceil(newCapacity/64)*64;
        else if (newCapacity < 1024*1024)
            newCapacity = Math.ceil(newCapacity/128)*128;
        else
            newCapacity = Math.ceil(newCapacity/1024)*1024;

        //console.log(`capacity old: ${this._capacity}, new: ${newCapacity}\n`);

        const newBuff = new ArrayBuffer(newCapacity);
        //const len = Math.min(newCapacity, this._data.byteLength);
        new Uint8Array(newBuff).set(new Uint8Array(this._data.buffer));         // copy
        this._capacity = newBuff.byteLength;
        this._data = new DataView(newBuff);

        if (this._limit >= this._capacity) {
            this._limit = this._capacity;
        }
        if (this._position >= this._capacity)
        {
            this._position = this._capacity;
        }
        if (this._mark >= this._capacity)
        {
            this._mark = this._capacity;
        }
        return this;
    }

    position() { return this._position; }

    setPosition(newPosition)  {
        if (newPosition > this._limit) {
            throw new Error("newPosition(" + newPosition + ") is larger than limit(" + this._limit + ")");
        }

        if (newPosition < this._mark) {
            // discard or set the same value
            this._position = this._mark;
        } else {
            this._position = newPosition;
        }

        return this;
    }

    limit() { return this._limit; }

    setLimit(newLimit) {
        if (newLimit < 0) {
            throw new Error("newLimit(" + newLimit + ") is les than 0");
        }

        if (newLimit > this._capacity) {
            //_limit = _capacity;
            // expand size
            //System.out.printf("new lim: %d\n", newLimit);
            this.setCapacity(newLimit);
        }

        this._limit = newLimit;
        if (this._mark > this._limit) {
            this._mark = this._limit;
        }
        if (this._position > this._limit) {
            this._position = this._limit;
        }

        return this;
    }

    /**
     * The bytes between the buffer's current position and its limit, if any, are copied to the beginning of the buffer.
     * This is user for read. If use for write, the copy is meaningless.
     * @return
     */
    compact() {
        if (this._position===0) return this;
        this._data = new DataView(this._data.buffer.slice(this._position, this._limit-this._position));
        //System.arraycopy(_data, _position, _data, 0, _limit-_position);
        this._limit = this._limit - this._position;
        this._position = 0;
        this._mark = 0;
        return this;
    }

    remaining() { return this._limit - this._position; }
    hasRemaining() { return this.remaining()>0; }

    /// ====== Position operation

    /**
     * Mark the current position, and go back later.
     * @return
     */
    mark() {
        this._mark = this._position;
        return this;
    }

    /**
     * Go to mark position.
     * @return
     */
    reset() {
        this._position = this._mark;
        return this;
    }

    /**
     * A new sequence, all reset to default.
     * Clears this buffer. The position is set to zero, the limit is set to the capacity, and the mark is discarded.
     * @return
     */
    clear() {
        this._position = 0;
        this._mark = 0;
        this._limit = this._capacity;
        return this;
    }

    /**
     * Flips this buffer. The limit is set to the current position and then the position is set to zero.
     * If the mark is defined then it is discarded.
     * This is use for write and then read.
     * @return
     */
    flip() {
        this._limit = this._position;
        this._position = 0;
        this._mark = 0;
        return this;
    }

    /**
     * Repeat a sequence again. The position is set to zero and the mark is discarded.
     * @return
     */
    rewind() {
        this._position = 0;
        this._mark = 0;
        return this;
    }

    /**
     * Subclass should override this to support loading automatically.
     * @param size
     */
    prepareForRead(size) {
        if (/*this._position<0 ||*/ this._position+size>this._limit) {
            throw new RangeError(`BufferUnderflowException, this._position:${this._position}, size:${size}, this._limit:${this._limit}`);
        }
    }

    /**
     * Subclass should override this to support loading automatically.
     * @param index
     * @param size
     */
    prepareForReadAt(index, size) {
        if (/*index<0 ||*/ index+size>this._limit) {
            throw new RangeError(`BufferUnderflowException, index:${index}, size:${size}, this._limit:${this._limit}`);
        }
    }

    /**
     * Subclass should override this function or override the functions: getLastBytes(), getLastString() to improve memory usage.
     */
    prepareAllForRead() {
        ///
    }

    /**
     * Subclass should override this to support storing automatically.
     * @param size
     */
    prepareForWrite(size) {
        /*if (_position<0) {
            throw new IndexOutOfBoundsException();
        }*/

        if (this._position+size>this._limit) {
            this.setLimit(this._position+size);
        }
    }

    /**
     * Subclass should override this to support storing automatically.
     * @param index
     * @param size
     */
    prepareForWriteAt(index, size) {
        /*if (index<0) {
            throw new RangeError("IndexOutOfBoundsException");
        }*/

        if (index+size>this._limit) {
            this.setLimit(index+size);
        }
    }

    /**
     * Subclass should override this to support storing immediately.
     * This will not be called before object release. Try to call this manually.
     * After the function have called, no other functions should be called.
     * If subclass doesn't support writing, this function should leave empty instead of
     * throwing an exception.
     */
    flushAllForWrite() {

    }

    /**
     * Subclass should override this to throw an exception if not support.
     */
    checkFullDataAlgorithmSupport() {

    }

    //// =====   Get & Put codes
    // byte
    getByte() {
        this.prepareForRead(1);
        return this._data.getUint8(this._position++);
    }

    peekByte() {
        this.prepareForRead(1);
        return this._data.getUint8(this._position);
    }

    getByteAt(index) {
        this.prepareForReadAt(index, 1);
        return this._data.getUint8(index);
    }

    putByte(b) {
        this.prepareForWrite(1);
        this._data.setUint8(this._position++, b);
        return this;
    }

    putByteAt(index, b) {
        this.prepareForWriteAt(index, 1);
        this._data.setUint8(index, b);
        return this;
    }

    // bool
    getBool() {
        return !!this.getByte();
    }

    peekBool() {
        return !!this.peekByte();
    }

    getBoolAt(index) {
        return !!this.getByteAt(index);
    }

    putBool(b) {
        return this.putByte((!!b)?1:0);
    }

    putBoolAt(index, b) {
        return this.putByteAt(index, (!!b)?1:0);
    }


    // short
    getShort(littleEndian=false) {
        this.prepareForRead(2);
        const index = this._position;
        this._position += 2;
        return this._data.getInt16(index, littleEndian);
    }

    getUShort(littleEndian=false) {
        this.prepareForRead(2);
        const index = this._position;
        this._position += 2;
        return this._data.getUint16(index, littleEndian);
    }

    peekShort(littleEndian=false) {
        this.prepareForRead(2);
        return this._data.getInt16(this._position, littleEndian);
    }

    peekUShort(littleEndian=false) {
        this.prepareForRead(2);
        return this._data.getUint16(this._position, littleEndian);
    }

    getShortAt(index, littleEndian=false) {
        this.prepareForReadAt(index,2);
        return this._data.getInt16(index, littleEndian);
    }

    getUShortAt(index, littleEndian=false) {
        this.prepareForReadAt(index, 2);
        return this._data.getUint16(index, littleEndian);
    }

    putShort(value, littleEndian=false) {
        this.prepareForWrite(2);
        this._data.setInt16(this._position, value, littleEndian);
        this._position += 2;
        return this;
    }

    putUShort(value, littleEndian=false) {
        this.prepareForWrite(2);
        this._data.setUint16(this._position, value, littleEndian);
        this._position += 2;
        return this;
    }

    putShortAt(index, value, littleEndian=false) {
        this.prepareForWriteAt(index, 2);
        this._data.setInt16(index, value, littleEndian);
        return this;
    }

    putUShortAt(index, value, littleEndian=false) {
        this.prepareForWriteAt(index, 2);
        this._data.setUint16(index, value, littleEndian);
        return this;
    }

    // 3 bytes Int
    get3bytesUInt(littleEndian=false) {
        const v = this.get3bytesUIntAt(this._position, littleEndian);
        this._position += 3;
        return v;
    }

    peek3bytesUInt(littleEndian=false) {
        return this.get3bytesUIntAt(this._position, littleEndian);
    }

    get3bytesUIntAt(index, littleEndian=false) {
        this.prepareForReadAt(index, 3);

        let i;
        if (littleEndian) {
            i = ((this._data.getUint8(index+2)&0x00ff) << 16)
                | ((this._data.getUint16(index, true)&0x00ffff) << 0);
        } else {
            i = ((this._data.getUint16(index, false)&0x00ffff) << 8)
                | ((this._data.getUint8(index+2)&0x00ff) << 0);
        }

        return i;
    }

    put3bytesUInt(value, littleEndian=false) {
        this.put3bytesUIntAt(this._position, value, littleEndian);
        this._position+=3;
        return this;
    }

    put3bytesUIntAt(index, value, littleEndian=false) {
        this.prepareForWriteAt(index,3);
        if (littleEndian) {
            this._data.setUint16(index, value & 0x00ffff, true);
            this._data.setUint8(index+2, (value >> 16) & 0xff);
        } else {
            this._data.setUint16(index, (value >> 8) & 0x00ffff, false);
            this._data.setUint8(index+2, value & 0x00ff);
        }

        return this;
    }

    // Int
    getInt(littleEndian=false) {
        this.prepareForRead(4);
        const index = this._position;
        this._position += 4;
        return this._data.getInt32(index, littleEndian);
    }

    getUInt(littleEndian=false) {
        this.prepareForRead(4);
        const index = this._position;
        this._position += 4;
        return this._data.getUint32(index, littleEndian);
    }

    peekInt(littleEndian=false) {
        this.prepareForRead(4);
        return this._data.getInt32(this._position, littleEndian);
    }

    peekUInt(littleEndian=false) {
        this.prepareForRead(4);
        return this._data.getUint32(this._position, littleEndian);
    }

    getIntAt(index, littleEndian=false) {
        this.prepareForReadAt(index, 4);
        return this._data.getInt32(index, littleEndian);
    }

    getUIntAt(index, littleEndian=false) {
        this.prepareForReadAt(index, 4);
        return this._data.getUint32(index, littleEndian);
    }

    putInt(value, littleEndian=false) {
        this.prepareForWrite(4);
        this._data.setInt32(this._position, value, littleEndian);
        this._position += 4;
        return this;
    }

    putUInt(value, littleEndian=false) {
        this.prepareForWrite(4);
        this._data.setUint32(this._position, value, littleEndian);
        this._position += 4;
        return this;
    }

    putIntAt(index, value, littleEndian=false) {
        this.prepareForWriteAt(index, 4);
        this._data.setInt32(index, value, littleEndian);
        return this;
    }

    putUIntAt(index, value, littleEndian=false) {
        this.prepareForWriteAt(index, 4);
        this._data.setUint32(index, value, littleEndian);
        return this;
    }

    // long, UInt64, Int64
    getULong(length=8, littleEndian=false) {
        let v = this.getULongAt(this._position, length, littleEndian);
        this._position += length;
        return v;
    }

    peekULong(length=8, littleEndian=false) {
        return this.getULongAt(this._position, length, littleEndian);
    }

    getULongAt(index, length=8, littleEndian=false) {
        if (length<=0 || length > 8)
            throw new RangeError(`Incorrect length(${length}) for get long value.`);

        this.prepareForReadAt(index, length);

        if (length === 8) {
            return this._data.getBigUint64(index, littleEndian);
        }

        let result = 0n;
        if (littleEndian) {
            for (let offset = length - 1; offset >= 0; offset--) {
                result = (result << 8n) | BigInt(this._data.getUint8(index + offset));
            }
        } else {
            for (let offset = 0; offset < length; offset++) {
                result = (result << 8n) | BigInt(this._data.getUint8(index + offset));
            }
        }
        return result;
    }

    putULong(value, length=8, littleEndian=false) {
        this.putULongAt(this._position, value, length, littleEndian);
        this._position += length;
        return this;
    }

    putULongAt(index, value, length=8, littleEndian=false) {
        if (length<=0 || length > 8)
            throw new RangeError(`Incorrect length(${length}) for put long value.`);

        this.prepareForWriteAt(index, length);

        const big = typeof value === "bigint" ? value : BigInt(value);
        if (length === 8) {
            this._data.setBigUint64(index, BigInt.asUintN(64, big), littleEndian);
            return this;
        }

        const unsigned = BigInt.asUintN(length * 8, big);
        if (littleEndian) {
            for (let i = 0; i < length; i++) {
                this._data.setUint8(index + i, Number((unsigned >> BigInt(i * 8)) & 0xFFn));
            }
        } else {
            for (let i = 0; i < length; i++) {
                const shift = BigInt((length - 1 - i) * 8);
                this._data.setUint8(index + i, Number((unsigned >> shift) & 0xFFn));
            }
        }
        return this;
    }

    static _NUM32 = 32n;
    static _NUM40 = 40n;
    static _NUM48 = 48n;

    getVarULong() {
        const b = this.getByte();
        let result;
        if ((b&0x80)===0) {
            result = BigInt(b&0x7F);
        } else if ((b&0x40)===0) {
            result = BigInt((((b&0x3F)<<8) | (this.getByte() & 0x00FF))+0x80);
        } else if ((b&0x20)===0) {
            result = BigInt((((b&0x1F)<<16) | (this.getUShort() & 0x00FFFF))+0x4080);
        } else if ((b&0x10)===0) {
            result = BigInt((((b&0x0F)<<24) | (this.get3bytesUInt() & 0x00FFFFFF))+0x204080);
        } else if ((b&0x08)===0) {
            result = ((BigInt(b&0x07) << DyBuf._NUM32) | this.getULong(4)) + DyBuf._BASE5;
        } else if ((b&0x04)===0) {
            result = ((BigInt(b&0x03) << DyBuf._NUM40) | this.getULong(5)) + DyBuf._BASE6;
        } else if ((b&0x02)===0) {
            result = ((BigInt(b&0x01) << DyBuf._NUM48) | this.getULong(6)) + DyBuf._BASE7;
        } else if ((b&0x01)===0) {
            result = this.getULong(7) + DyBuf._BASE8;
        } else {
            result = this.getULong() + DyBuf._BASE9;
        }
        return BigInt.asUintN(64, result);
    }

    static _BASE5 = 0x10204080n;
    static _BASE6 = 0x0810204080n;
    static _BASE7 = 0x040810204080n;
    static _COMP7 = 0x01FFFFFFFFFFFFn + DyBuf._BASE7;
    static _CONST7 = 0xFC000000000000n;
    static _BASE8 = 0x02040810204080n;
    static _COMP8 = 0x00FFFFFFFFFFFFFFn + DyBuf._BASE8;
    static _CONST8 = 0xFE00000000000000n;
    static _BASE9 = 0x0102040810204080n;

    putVarULong(value) {
        let bigInt = typeof value === "bigint" ? value : BigInt(value);
        bigInt = BigInt.asUintN(64, bigInt);
        const numeric = Number(bigInt);

        if (numeric>=0 && numeric<=0x7F) {								// 0 ~ 0x7F
            return this.putByte(numeric);
        } else if (numeric>=0 && numeric <= (0x3FFF+0x80)) {
            // (0x7F+1) ~ 0x3FFF+(0x7F+1)
            // 0x80 ~ 0x3FFF+0x80
            return this.putUShort((0x8000 + (numeric-0x80)));
        } else if (numeric>=0 && numeric <= (0x1FFFFF+0x4080)) {
            // (0x3FFF+(0x7F+1)+1) ~ 0x1FFFFF+(0x3FFF+(0x7F+1)+1)
            // 0x4080 ~ 0x1FFFFF + 0x4080
            return this.put3bytesUInt((0xC00000 + (numeric-0x4080)));
        } else if (numeric>=0 && numeric <= (0x0FFFFFFF+0x204080)) {
            // (0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1) ~ 0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)
            // 0x204080 ~ 0x0FFFFFFF + 0x204080
            return this.putUInt(0xE0000000 + (numeric-0x204080));
        } else if (numeric>=0 && numeric <= (0x07FFFFFFFF+0x10204080)) {
            // (0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1) ~ 0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1)
            // 0x10204080 ~ 0x07FFFFFFFF+0x10204080
            return this.putULong(0xF000000000 + (numeric-0x10204080), 5);
        } else if (numeric>=0 && numeric <= (0x03FFFFFFFFFF+0x0810204080)) {
            //  (0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1)) ~ 0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))
            // 0x0810204080 ~ 0x03FFFFFFFFFF+0x0810204080
            return this.putULong(0xF80000000000 + (numeric-0x0810204080), 6);
        } else if (bigInt <= DyBuf._COMP7) {
            // *** JavaScript supports maximum integer: 0x1FFFFFFFFFFFFF, STARTING TO USE 'BigInt' ***
            // (0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1) ~ 0x01FFFFFFFFFFFF+(0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1)
            // 0x040810204080 ~ 0x01FFFFFFFFFFFF+0x040810204080
            return this.putULong((DyBuf._CONST7 | (bigInt - DyBuf._BASE7)), 7);
        } else if (bigInt <= DyBuf._COMP8) {
            // (0x01FFFFFFFFFFFF+(0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1)) ~ 0x00FFFFFFFFFFFFFF+((0x01FFFFFFFFFFFF+(0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1)))
            // 0x02040810204080 ~ 0x00FFFFFFFFFFFFFF+0x02040810204080
            return this.putULong((DyBuf._CONST8 | (bigInt - DyBuf._BASE8)), 8);
        } else {
            // 0x0102040810204080 ~ 0xFFFFFFFFFFFFFFFF
            this.putByte(0xFF);
            return this.putULong(bigInt - DyBuf._BASE9, 8);
        }
    }

    static _NUM1 = 1n;
    static _NUM63BITS = 0x7FFFFFFFFFFFFFFFn;
    static _NUM64BITS = 0xFFFFFFFFFFFFFFFFn;
    getVarLong() {
        const u = this.getVarULong();
        const su = (u >> DyBuf._NUM1) & DyBuf._NUM63BITS;
        const isNegative = (u & DyBuf._NUM1) === DyBuf._NUM1;
        return BigInt.asIntN(64, isNegative ? (su ^ DyBuf._NUM64BITS) : su);
    }

    static _NUM63 = 63n;
    putVarLong(value) {
        if (typeof value !== "bigint") value = BigInt(value);
        value = BigInt.asIntN(64, value);
        const ul = BigInt.asUintN(64, (value << DyBuf._NUM1) ^ (value >> DyBuf._NUM63));
        this.putVarULong(ul);
        return this;
    }

    // bytes
    getLastBytes() {
        this.prepareAllForRead();
        if (this._limit-this._position<=0) return null;
        const r = this._data.buffer.slice(this._position, this._limit);
        if (r.byteLength === 0) {
            this._position = this._limit;
            return new ArrayBuffer(0);
        }
        this._position = this._limit;
        return r;
    }

    getBytesWithFixedLength(length) {
        if (length < 0) return null;
        this.prepareForRead(length);
        if (length === 0) {
            return new ArrayBuffer(0);
        }
        const r = this._data.buffer.slice(this._position, this._position+length);
        this._position += length;
        return r;
    }

    getBytesWithOneByteLength() {
        let length = this.getByte();
        return this.getBytesWithFixedLength(length);
    }

    getBytesWithTwoByteLength() {
        let length = this.getUShort();
        return this.getBytesWithFixedLength(length);
    }

    getBytesWithVarLength() {
        const length = Number(this.getVarULong());
        return this.getBytesWithFixedLength(length);
    }

    putLastBytes(arrayBuf) {
        if (arrayBuf==null) return this;
        if (!(arrayBuf instanceof ArrayBuffer)) {
            throw new TypeError("Only support ArrayBuffer argument in this moment.");
        }

        const l = arrayBuf.byteLength;
        this.prepareForWrite(l);
        const view = new Uint8Array(arrayBuf);
        new Uint8Array(this._data.buffer).set(view, this._position);
        this._position += l;

        return this;
    }

    putBytesWithOneByteLength(arrayBuf) {
        return this.putBytesWithVarLength(arrayBuf, 1);
    }

    putBytesWithTwoByteLength(arrayBuf) {
        return this.putBytesWithVarLength(arrayBuf, 2);
    }

    putBytesWithVarLength(arrayBuf, sizeForCount=0) {
        if (arrayBuf!=null && !(arrayBuf instanceof ArrayBuffer)) {
            throw new TypeError("Only support ArrayBuffer argument in this moment.");
        }
        const l = arrayBuf==null ? 0 : arrayBuf.byteLength;
        switch (sizeForCount) {
            case 0: {
                this.putVarULong(l);
                break;
            }
            case 1: {
                if (l>255) {
                    throw new RangeError(`Put data length(${l}) > 255`);
                }
                this.putByte(l);
                break;
            }
            case 2: {
                if (l>65535) {
                    throw new RangeError(`Put data length(${l}) > 65535`);
                }
                this.putUShort(l);
                break;
            }
            default: {
                throw new RangeError(`Non supported size(${l}) for bytes count.`);
            }
        }

        return this.putLastBytes(arrayBuf);
    }

    // string
    getLastString() {
        const buf = this.getLastBytes();
        return buf == null ? null : new TextDecoder('utf-8').decode(buf);
    }

    getStringWithFixedLength(length) {
        const buf = this.getBytesWithFixedLength(length);
        if (buf == null) return null;
        return new TextDecoder('utf-8').decode(buf);
    }

    getStringWithOneByteLength() {
        const buf = this.getBytesWithOneByteLength();
        if (buf == null) return null;
        return new TextDecoder('utf-8').decode(buf);
    }

    getStringWithTwoByteLength() {
        const buf = this.getBytesWithTwoByteLength();
        if (buf == null) return null;
        return new TextDecoder('utf-8').decode(buf);
    }

    getCStringWithVarLength() {
        const buf = this.getBytesWithVarLength();
        if (buf == null) return null;
        const bytes = new Uint8Array(buf);
        let end = bytes.length;
        if (end > 0 && bytes[end - 1] === 0) {
            end -= 1;
        }
        if (end === 0) return '';
        return new TextDecoder('utf-8').decode(bytes.subarray(0, end));
    }

    getVarString() {
        const buf = this.getBytesWithVarLength();
        if (buf == null) return null;
        return new TextDecoder('utf-8').decode(buf);
    }

    putLastString(s) {
        return this.putLastBytes(new TextEncoder('utf-8').encode(s).buffer);
    }

    putStringWithOneByteLength(s) {
        return this.putBytesWithOneByteLength(new TextEncoder('utf-8').encode(s).buffer);
    }

    putStringWithTwoByteLength(s) {
        return this.putBytesWithTwoByteLength(new TextEncoder('utf-8').encode(s).buffer);
    }

    putCStringWithVarLength(s) {
        const encoded = new TextEncoder('utf-8').encode(s);
        const payload = new Uint8Array(encoded.length + 1);
        payload.set(encoded);
        return this.putBytesWithVarLength(payload.buffer);
    }

    putVarString(s) {
        return this.putBytesWithVarLength(new TextEncoder('utf-8').encode(s).buffer);
    }

    // Legacy aliases for compatibility
    getStringWithVarLength() {
        return this.getVarString();
    }

    putStringWithVarLength(s) {
        return this.putVarString(s);
    }

    // double, float
    getFloat(littleEndian=false) {
        this.prepareForRead(4);
        const index = this._position;
        this._position += 4;
        return this._data.getFloat32(index, littleEndian);
    }

    putFloat(value, littleEndian=false) {
        this.prepareForWrite(4);
        this._data.setFloat32(this._position, value, littleEndian);
        this._position += 4;
        return this;
    }

    getDouble(littleEndian=false) {
        this.prepareForRead(8);
        const index = this._position;
        this._position += 8;
        return this._data.getFloat64(index, littleEndian);
    }

    putDouble(value, littleEndian=false) {
        this.prepareForWrite(8);
        this._data.setFloat64(this._position, value, littleEndian);
        this._position += 8;
        return this;
    }

    // index and 4bits type
    putTypdex(type, index) {
        if (!Number.isInteger(index) || !Number.isInteger(type) || index < 0 || type < 0) {
            throw new RangeError('typdex type/index must be non-negative integers');
        }
        if (type <= 0x0F && index <= 0x07) {
            this.putByte((type << 3) | index);
        } else if (type <= 0x3F && index <= 0xFF) {
            this.putUShort(0x8000 | (type << 8) | index);
        } else if (type <= 0xFF && index <= 0x1FFF) {
            this.put3bytesUInt(0xC00000 | (type << 13) | index);
        } else if (type <= 0xFF && index <= 0x0FFFFF) {
            this.putUInt(0xE0000000 | (type << 20) | index);
        } else {
            throw new RangeError(`typdex type/index out of supported range (type=${type}, index=${index})`);
        }
        return this;
    }

    getTypdex() {
        const header = this.peekByte();
        let type;
        let index;
        if ((header & 0x80) === 0) {
            const v = this.getByte();
            type = (v >> 3) & 0x0F;
            index = v & 0x07;
        } else if ((header & 0x40) === 0) {
            const v = this.getUShort();
            type = (v >> 8) & 0x3F;
            index = v & 0xFF;
        } else if ((header & 0x20) === 0) {
            const v = this.get3bytesUInt();
            type = (v >> 13) & 0xFF;
            index = v & 0x1FFF;
        } else if ((header & 0x10) === 0) {
            const v = this.getUInt();
            type = (v >> 20) & 0xFF;
            index = v & 0x0FFFFF;
        } else {
            throw new RangeError(`Malformed typdex header: 0x${header.toString(16)}`);
        }

        return {
            type,
            index,
        };
    }

    // Legacy aliases for backward compatibility
    putIndexAnd4bitsType(index, type) {
        return this.putTypdex(type, index);
    }

    getIndexAnd4bitsType() {
        const { type, index } = this.getTypdex();
        return { type, value: index };
    }

    //
    toBytesBeforeCurrentPosition() {
        return this._data.buffer.slice(this._mark, this._position);
    }

}

const MAX_SAFE_BIGINT = BigInt(Number.MAX_SAFE_INTEGER);
const MIN_SAFE_BIGINT = -MAX_SAFE_BIGINT;

export function encodeJson(value) {
    const buf = new DyBuf(256);
    appendJsonValue(buf, value);
    return buf.toBytesBeforeCurrentPosition();
}

export function decodeJson(data) {
    const buf = new DyBuf(_arrayBufferFromJsonInput(data));
    const value = nextJsonValue(buf);
    if (buf.remaining() !== 0) {
        throw new Error('trailing bytes after JSON-dybuf payload');
    }
    return value;
}

export function appendJsonValue(buf, value) {
    const dictionaries = _buildJsonDictionaries(value);

    buf.putTypdex(TYPDEX_TYP_OBJ, 0);
    buf.putVarULong(JSON_DYBUF_FORMAT_VERSION);
    buf.putVarULong(dictionaries.size);
    for (const [path, keyToIndex] of dictionaries.entries()) {
        buf.putVarString(path);
        buf.putVarULong(keyToIndex.size);
        for (const key of keyToIndex.keys()) {
            buf.putVarString(key);
        }
    }

    buf.putTypdex(TYPDEX_TYP_OBJ, 1);
    _putJsonRecord(buf, value, '$', 0, dictionaries);
    return buf;
}

export function nextJsonValue(buf) {
    let marker = buf.getTypdex();
    if (marker.type !== TYPDEX_TYP_OBJ || marker.index !== 0) {
        throw new Error('JSON-dybuf stream must start with TYPDEX_TYP_OBJ, 0');
    }

    const version = _readJsonCount(buf, 'json_dybuf_format_version');
    if (version !== JSON_DYBUF_FORMAT_VERSION) {
        throw new Error(`unsupported JSON-dybuf format version: ${version}`);
    }

    const dictionaryCount = _readJsonCount(buf, 'dictionary_count');
    const dictionaries = new Map();
    for (let i = 0; i < dictionaryCount; i += 1) {
        const path = buf.getVarString();
        if (dictionaries.has(path)) {
            throw new Error(`duplicate JSON dictionary path: ${path}`);
        }
        const keyCount = _readJsonCount(buf, `key_count for ${path}`);
        const keys = [];
        const seen = new Set();
        for (let j = 0; j < keyCount; j += 1) {
            const key = buf.getVarString();
            if (seen.has(key)) {
                throw new Error(`duplicate key in JSON dictionary ${path}: ${key}`);
            }
            seen.add(key);
            keys.push(key);
        }
        dictionaries.set(path, keys);
    }

    marker = buf.getTypdex();
    if (marker.type !== TYPDEX_TYP_OBJ || marker.index !== 1) {
        throw new Error('JSON-dybuf payload marker must be TYPDEX_TYP_OBJ, 1');
    }

    const root = buf.getTypdex();
    return _getJsonPayload(buf, root.type, root.index, '$', dictionaries);
}

function _buildJsonDictionaries(value) {
    const dictionaries = new Map();

    function ensure(path) {
        let dictionary = dictionaries.get(path);
        if (dictionary == null) {
            dictionary = new Map();
            dictionaries.set(path, dictionary);
        }
        return dictionary;
    }

    function visit(current, path) {
        if (_isJsonObject(current)) {
            const dictionary = ensure(path);
            for (const key of Object.keys(current)) {
                if (!dictionary.has(key)) {
                    dictionary.set(key, dictionary.size);
                }
                visit(current[key], `${path}.${dictionary.get(key)}`);
            }
            return;
        }
        if (Array.isArray(current)) {
            for (const child of current) {
                visit(child, `${path}.[]`);
            }
            return;
        }
        _jsonTypdexType(current);
    }

    visit(value, '$');
    return dictionaries;
}

function _putJsonRecord(buf, value, path, index, dictionaries) {
    const type = _jsonTypdexType(value);
    buf.putTypdex(type, index);

    switch (type) {
        case TYPDEX_TYP_NONE:
            return;
        case TYPDEX_TYP_BOOL:
            buf.putBool(value);
            return;
        case TYPDEX_TYP_INT:
            buf.putVarLong(BigInt(value));
            return;
        case TYPDEX_TYP_UINT:
            buf.putVarULong(BigInt(value));
            return;
        case TYPDEX_TYP_DOUBLE:
            buf.putDouble(value);
            return;
        case TYPDEX_TYP_STRING:
            buf.putVarString(value);
            return;
        case TYPDEX_TYP_ARRAY: {
            buf.putVarULong(value.length);
            const childPath = `${path}.[]`;
            for (const child of value) {
                _putJsonRecord(buf, child, childPath, 0, dictionaries);
            }
            return;
        }
        case TYPDEX_TYP_MAP: {
            const dictionary = dictionaries.get(path);
            if (dictionary == null) {
                throw new Error(`missing JSON dictionary for path: ${path}`);
            }
            buf.putVarULong(Object.keys(value).length);
            for (const key of Object.keys(value)) {
                const keyIndex = dictionary.get(key);
                _putJsonRecord(buf, value[key], `${path}.${keyIndex}`, keyIndex, dictionaries);
            }
            return;
        }
        default:
            throw new Error(`unhandled JSON typdex type: ${type}`);
    }
}

function _getJsonPayload(buf, type, index, path, dictionaries) {
    switch (type) {
        case TYPDEX_TYP_NONE:
            return null;
        case TYPDEX_TYP_BOOL:
            return buf.getBool();
        case TYPDEX_TYP_INT:
            return _safeJsonNumber(buf.getVarLong(), 'signed integer');
        case TYPDEX_TYP_UINT:
            return _safeJsonNumber(buf.getVarULong(), 'unsigned integer');
        case TYPDEX_TYP_DOUBLE:
            return _finiteJsonDouble(buf.getDouble());
        case TYPDEX_TYP_STRING:
            return buf.getVarString();
        case TYPDEX_TYP_ARRAY: {
            const count = _readJsonCount(buf, 'array item_count');
            const childPath = `${path}.[]`;
            const result = [];
            for (let i = 0; i < count; i += 1) {
                const child = buf.getTypdex();
                result.push(_getJsonPayload(buf, child.type, child.index, childPath, dictionaries));
            }
            return result;
        }
        case TYPDEX_TYP_MAP: {
            const keys = dictionaries.get(path);
            if (keys == null) {
                throw new Error(`missing JSON dictionary for path: ${path}`);
            }
            const presentCount = _readJsonCount(buf, `present_count for ${path}`);
            const result = {};
            const seenIndices = new Set();
            for (let i = 0; i < presentCount; i += 1) {
                const child = buf.getTypdex();
                if (seenIndices.has(child.index)) {
                    throw new Error(`duplicate key index ${child.index} in JSON object ${path}`);
                }
                if (child.index >= keys.length) {
                    throw new Error(`key index ${child.index} out of range for JSON object ${path}`);
                }
                seenIndices.add(child.index);
                result[keys[child.index]] = _getJsonPayload(
                    buf,
                    child.type,
                    child.index,
                    `${path}.${child.index}`,
                    dictionaries
                );
            }
            return result;
        }
        default:
            throw new Error(`unsupported JSON typdex type: ${type}`);
    }
}

function _jsonTypdexType(value) {
    if (value === null) {
        return TYPDEX_TYP_NONE;
    }
    if (typeof value === 'boolean') {
        return TYPDEX_TYP_BOOL;
    }
    if (typeof value === 'number') {
        if (!Number.isFinite(value)) {
            throw new Error('JSON number must be finite');
        }
        if (Number.isInteger(value)) {
            if (!Number.isSafeInteger(value)) {
                throw new Error('JSON integer is outside JavaScript safe integer range');
            }
            return value < 0 ? TYPDEX_TYP_INT : TYPDEX_TYP_UINT;
        }
        return TYPDEX_TYP_DOUBLE;
    }
    if (typeof value === 'string') {
        return TYPDEX_TYP_STRING;
    }
    if (Array.isArray(value)) {
        return TYPDEX_TYP_ARRAY;
    }
    if (_isJsonObject(value)) {
        return TYPDEX_TYP_MAP;
    }
    throw new TypeError(`unsupported JSON value type: ${typeof value}`);
}

function _isJsonObject(value) {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) {
        return false;
    }
    const proto = Object.getPrototypeOf(value);
    return proto === Object.prototype || proto === null;
}

function _readJsonCount(buf, label) {
    const value = buf.getVarULong();
    if (value > MAX_SAFE_BIGINT) {
        throw new Error(`${label} is too large`);
    }
    return Number(value);
}

function _safeJsonNumber(value, label) {
    if (value < MIN_SAFE_BIGINT || value > MAX_SAFE_BIGINT) {
        throw new Error(`decoded JSON ${label} is outside JavaScript safe integer range`);
    }
    return Number(value);
}

function _finiteJsonDouble(value) {
    if (!Number.isFinite(value)) {
        throw new Error('decoded JSON double must be finite');
    }
    return value;
}

function _arrayBufferFromJsonInput(data) {
    if (data instanceof ArrayBuffer) {
        return data;
    }
    if (ArrayBuffer.isView(data)) {
        return data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength);
    }
    throw new TypeError('decodeJson expects an ArrayBuffer or ArrayBuffer view');
}

// eslint-disable-next-line no-unused-vars
export function testDyBuf() {
    let buff = new DyBuf(1024);
    let i;
    for (i=1; i<=8; i++) {
        buff.putULong(i, i);
        buff.putULong(i, i, true)
    }
    buff.putULong(-1);
    buff.putULong(0xffffffff);
    buff.putULong(0xffffffff+1);
    let len = buff.position();
    console.log(`len=${len}`);
    buff.putVarULong(1);
    let len_add1 = buff.position();
    console.log(`len_add1=${len_add1}`);
    buff.putVarULong(-1);
    let len_add_m1 = buff.position();
    console.log(`len_add_m1=${len_add_m1}`);
    buff.rewind();
    for (i=1; i<=8; i++) {
        console.log(buff.getULong(i).toString(10));
        console.log(buff.getULong(i, true).toString(10));
    }
    console.log(buff.getULong().toString(10));
    console.log(buff.getULong().toString(10));
    console.log(buff.getULong().toString(10));
    const ll = 0x0102040810204080n;
    console.log("0x0102040810204080");
    console.log(ll.toString(16));
    console.log(buff.getVarULong().toString(10));
    console.log(buff.getVarULong().toString(10));
    //console.log(Number(buff.getVarULong()));
    /*console.log(`1 use ${len_add1-len} bytes`);
    console.log(`-1 use ${len_add_m1-len_add1} bytes`);
    let max = 0xffffffffffffffffn;
    console.log(`${Number(max)} ==? ${max.toString(16)}`);
    let num_m1 =  0xffffn;
    console.log(`${Number(num_m1)} ==? ${num_m1.toString(10)}`);
    num_m1 -= 1n;
    console.log(`${Number(num_m1)} ==? ${num_m1.toString(10)}`);*/

    console.log("==========");
    /*buff = new DyBuf(1024);
    buff.putVarLong(0x8000000000000000n);
    buff.rewind();
    console.log(buff.getVarLong().toString(16));*/

    let max = 0xffffffffffffffffn;
    console.log(`${Number(max)} ==? ${max.toString(16)}`);
    let num_m1 = -1n;
    console.log(`${Number(num_m1)} ==? ${num_m1.toString(16)}`);

    console.log((0x8000000000000000n >> 63n).toString(16));

    console.log((BigInt.asUintN(64, 0x8000000000000000n) >> 63n).toString(16));
    console.log((BigInt.asIntN(64, 0x8000000000000000n) >> 63n).toString(16));

    setTimeout(testDyBufDetail, 100, 0x8000000000000000n);
}

function testDyBufDetail(cur) {
    //let cur = BigInt(defaultValue);
    const max = 0n;
    if (cur === max) return;
    let i;
    let step = cur;
    let buf = new DyBuf(64);

    for (i=0;i<1000;i++) {
        buf.putVarLong(step);
        step += DyBuf._NUM1;
    }

    console.log(`${cur.toString(10)} ~ ${step.toString(10)}: length = ${buf.position()}`);
    buf.rewind();
    step = cur;

    for (i=0;i<1000;i++) {
        cur = buf.getVarLong();
        // remove following comment
        if (cur !== BigInt.asIntN(64, step)) {
            console.log(`${cur.toString(16)} != ${step.toString(16)}`);
            return;
        }
        step += DyBuf._NUM1;
    }

    setTimeout(testDyBufDetail, 100, cur);
}

export default DyBuf;
