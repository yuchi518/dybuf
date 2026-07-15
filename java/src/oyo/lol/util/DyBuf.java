/*
 * DyBuf
 * Copyright (C) 2015 Yuchi (yuchi518@gmail.com)

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation. For the terms of this
 * license, see <http://www.gnu.org/licenses>.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package oyo.lol.util;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DyBuf {
    static Logger log = Logger.getLogger(DyBuf.class.getName());

    public static final byte TYPDEX_TYP_NONE = 0x00;
    public static final byte TYPDEX_TYP_BOOL = 0x01;
    public static final byte TYPDEX_TYP_INT = 0x02;
    public static final byte TYPDEX_TYP_UINT = 0x03;
    public static final byte TYPDEX_TYP_FLOAT = 0x06;
    public static final byte TYPDEX_TYP_DOUBLE = 0x07;
    public static final byte TYPDEX_TYP_STRING = 0x0A;
    public static final byte TYPDEX_TYP_BYTES = 0x0B;
    public static final byte TYPDEX_TYP_ARRAY = 0x0C;
    public static final byte TYPDEX_TYP_MAP = 0x0D;
    public static final byte TYPDEX_TYP_OBJ = 0x0E;
    public static final byte TYPDEX_TYP_F = 0x0F;

    public static final int DYPE_F_EOF = 0;
    public static final int DYPE_F_VERSION = 1;
    public static final int DYPE_F_PROTOCOL = 7;
    public static final int DYPE_F_PROTO_VERSION = 8;
    public static final int JSON_DYBUF_FORMAT_VERSION = 1;
    private static final long MAX_JSON_SAFE_INTEGER = (1L << 53) - 1;
    private static final long MIN_JSON_SAFE_INTEGER = -MAX_JSON_SAFE_INTEGER;


    /**
     * TO-DO: automatically free cached memory block
     */
    static SortedMap<Integer, Queue<byte[]>> memcache = new TreeMap<Integer, Queue<byte[]>>();
    final static int CACHE_SIZE_UNIT = 16;
    final static int MAX_CACHE_SIZE = 1024 * 32;
    final static double log2 = Math.log(2);
    public static int hitC = 0, misC = 0;
    public static long totalCachedSize = 0;
    public static long totalReusedSize = 0;
    public static long maxRequestSize = 0;

    static byte[] allocBytes(int size) {
        //size = size==0?CACHE_SIZE_UNIT:(size%CACHE_SIZE_UNIT==0)?size:(size/CACHE_SIZE_UNIT+1)*CACHE_SIZE_UNIT;
        maxRequestSize = Math.max(size, maxRequestSize);
        if (size < CACHE_SIZE_UNIT) {
            size = CACHE_SIZE_UNIT;
        } else {
            size = CACHE_SIZE_UNIT * (int) Math.pow(2, Math.ceil(Math.log(1.0 * size / CACHE_SIZE_UNIT) / log2));
        }

        // force to enlarge faster
        if (size > MAX_CACHE_SIZE) {
            return new byte[size];
        }
        //if (size>100000) throw new RuntimeException("What");
        //System.out.printf("allsize %d\n", size);
        synchronized (memcache) {
            Queue<byte[]> dq = memcache.get(size);
            if (dq == null || dq.size() == 0) {
                misC++;
                return new byte[size];
            }
            byte bytes[] = dq.poll();
            hitC++;
            totalCachedSize -= bytes.length;
            totalReusedSize += bytes.length;
            return bytes;
        }
    }

    static void releaseBytes(byte bs[]) {
        int size = bs.length;
        if (size < CACHE_SIZE_UNIT || size > MAX_CACHE_SIZE) return;
        //size = (size/CACHE_SIZE_UNIT)*CACHE_SIZE_UNIT;		// we should put in less-value queue, instead of larger-value queue.
        size = CACHE_SIZE_UNIT * (int) Math.pow(2, Math.floor(Math.log(1.0 * size / CACHE_SIZE_UNIT) / log2));
        //System.out.printf("Rel size: %d\n", size);
        synchronized (memcache) {
            Queue<byte[]> dq = memcache.get(size);
            if (dq == null) {
                dq = new ArrayDeque<byte[]>();
                memcache.put(size, dq);
            }
            dq.add(bs);
            totalCachedSize += bs.length;
        }
    }

    /**
     * 0 <= mark <= position <= limit <= capacity
     * 1. clear() makes a buffer ready for a new sequence of channel-read or relative put operations:
     * It sets the limit to the capacity and the position to zero.
     * 2. flip() makes a buffer ready for a new sequence of channel-write or relative get operations:
     * It sets the limit to the current position and then sets the position to zero.
     * 3. rewind() makes a buffer ready for re-reading the data that it already contains:
     * It leaves the limit unchanged and sets the position to zero.
     */

    byte _data[];
    int _capacity;
    int _limit;
    int _position;
    int _mark;

    boolean fixedCapacity = false;        // default is dynamic enlarge when write

    public DyBuf(int capacity) {
        _data = allocBytes(capacity);
        _capacity = _data.length;
        _limit = 0;
        _position = 0;
        _mark = 0;
    }

    public DyBuf(byte data[], boolean noCopy) {
        if (data == null) {
            _data = allocBytes(CACHE_SIZE_UNIT);
            _capacity = _limit = _data.length;
        } else {
            if (noCopy) {
                _data = data;
                _capacity = _limit = _data.length;
            } else {
                _data = allocBytes(data.length);
                System.arraycopy(data, 0, _data, 0, data.length);
                _capacity = _data.length;
                _limit = data.length;
            }
        }

        _position = _mark = 0;
    }

    public DyBuf(InputStream input) {
        this(DyBuf.readAllBytes(input), true);
    }

    /**
     * Don't call this before using.
     * After calling, no other operation should be called.
     * If you have no idea when to call, DON'T CALL.
     */
    public void releaseForReuse() {
        flushAllForWrite();
        releaseBytes(_data);
        _data = null;
    }

    /// ======= data maintenance

    public final int capacity() {
        return _capacity;
    }

    protected final DyBuf capacity(int newCapacity) {
        if (fixedCapacity && (newCapacity != _capacity)) {
            throw new IndexOutOfBoundsException("Capacity can't be changed");
        }

        //System.out.printf("old d: %d for %d\n", _data.length, newCapacity);
        if (newCapacity == _capacity) return this;

        byte newData[] = allocBytes(newCapacity);
        System.arraycopy(_data, 0, newData, 0, Math.min(_capacity, newData.length));
        _capacity = newData.length;
        releaseBytes(_data);
        _data = newData;
        newData = null;

        if (_limit >= _capacity) {
            _limit = _capacity;
        }

        if (_position >= _capacity) {
            _position = _capacity;
        }

        if (_mark >= _capacity) {
            _mark = _capacity;
        }

        //System.out.printf("new d: %d\n", _data.length);
        return this;
    }

    public int position() {
        return _position;
    }

    public DyBuf position(int newPosition) {
        if (newPosition > _limit) {
            throw new IllegalArgumentException("newPosition(" + newPosition + ") is larger than limit(" + _limit + ")");
        }

        if (newPosition < _mark) {
            // discard or set the same value
            _position = _mark;
        } else {
            _position = newPosition;
        }

        return this;
    }

    public final int limit() {
        return _limit;
    }

    protected final DyBuf limit(int newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException("newLimit(" + newLimit + ") is les than 0");
        }

        if (newLimit > _capacity) {
            //_limit = _capacity;
            // expand size
            //System.out.printf("new lim: %d\n", newLimit);
            capacity(newLimit);
        }

        _limit = newLimit;

        if (_mark > _limit) {
            _mark = _limit;
        }

        if (_position > _limit) {
            _position = _limit;
        }

        return this;
    }


    /**
     * The bytes between the buffer's current position and its limit, if any, are copied to the beginning of the buffer.
     * This is user for read. If use for write, the copy is meaningless.
     *
     * @return
     */
    public DyBuf compact() {
        if (_position == 0) return this;
        System.arraycopy(_data, _position, _data, 0, _limit - _position);
        _limit = _limit - _position;
        _position = 0;
        _mark = 0;
        return this;
    }

    public int remaining() {
        return _limit - _position;
    }

    public boolean hasRemaining() {
        return remaining() > 0;
        //return (_limit - _position) > 0;
    }


    /// ====== Position operation

    /**
     * Mark the current position, and go back later.
     *
     * @return
     */
    public DyBuf mark() {
        _mark = _position;

        return this;
    }

    /**
     * Go to mark position.
     *
     * @return
     */
    public DyBuf reset() {
        _position = _mark;

        return this;
    }

    /**
     * A new sequence, all reset to default.
     * Clears this buffer. The position is set to zero, the limit is set to the capacity, and the mark is discarded.
     *
     * @return
     */
    public DyBuf clear() {
        _position = 0;
        _mark = 0;
        _limit = _capacity;
        return this;
    }

    /**
     * Flips this buffer. The limit is set to the current position and then the position is set to zero.
     * If the mark is defined then it is discarded.
     * This is use for write and then read.
     *
     * @return
     */
    public DyBuf flip() {
        _limit = _position;
        _position = 0;
        _mark = 0;
        return this;
    }

    /**
     * Repeat a sequence again. The position is set to zero and the mark is discarded.
     *
     * @return
     */
    public DyBuf rewind() {
        _position = 0;
        _mark = 0;

        return this;
    }

	/*public boolean isReadOnly()
	{
		return false;
	}*/
	
	/*public boolean hasArray()
	{
		return true;
	}
	
	public byte[] array()
	{
		return _data;
	}*/
	
	/*public int arrayOffset()
	{
		return 0;
	}
	
	public boolean isDirect()
	{
		return false;
	}
	
	
	
	public CharBuffer asCharBuffer() {
		// TODO: Auto-generated method stub
		return null;
	}

	
	public DoubleBuffer asDoubleBuffer() {
		// TODO: Auto-generated method stub
		return null;
	}

	
	public FloatBuffer asFloatBuffer() {
		// TODO: Auto-generated method stub
		return null;
	}

	
	public IntBuffer asIntBuffer() {
		// TODO: Auto-generated method stub
		return null;
	}

	
	public LongBuffer asLongBuffer() {
		// TODO: Auto-generated method stub
		return null;
	}

	
	public DyBuf asReadOnlyBuffer() {
		// TODO: Auto-generated method stub
		return null;
	}

	
	public ShortBuffer asShortBuffer() {
		// TODO: Auto-generated method stub
		return null;
	}*/

    //// ====

    /**
     * Subclass should override this to support loading automatically.
     *
     * @param size
     */
    protected void prepareForRead(int size) {
        if (/*_position<0 ||*/ _position + size > _limit) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * Subclass should override this to support loading automatically.
     *
     * @param index
     * @param size
     */
    protected void prepareForRead(int index, int size) {
        if (/*index<0 ||*/ index + size > _limit) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * Subclass should override this function or override the functions: getLastBytes(), getLastString() to improve memory usage.
     */
    protected void prepareAllForRead() {
        ///
    }

    /**
     * Subclass should override this to support storing automatically.
     *
     * @param size
     */
    protected void prepareForWrite(int size) {
		/*if (_position<0) {
			throw new IndexOutOfBoundsException();
		}*/

        if (_position + size > _limit) {
            limit(_position + size);
        }
    }

    /**
     * Subclass should override this to support storing automatically.
     *
     * @param index
     * @param size
     */
    protected void prepareForWrite(int index, int size) {
		/*if (_position<0) {
			throw new IndexOutOfBoundsException();
		}*/

        if (index + size > _limit) {
            limit(index + size);
        }
    }

    /**
     * Subclass should override this to support storing immediately.
     * This will not be called before object release. Try to call this manually.
     * After the function have called, no other functions should be called.
     * If subclass doesn't support writing, this function should leave empty instead of
     * throwing an exception.
     */
    public void flushAllForWrite() {

    }

    /**
     * Subclass should override this to throw an exception if not support.
     */
    protected void checkFullDataAlgorithmSupport() {

    }


    //// =====   Get & Put codes
    //// ======


    public byte get() {
        prepareForRead(1);

        return _data[_position++];
    }

    public byte peek() {
        prepareForRead(1);

        return _data[_position];
    }

    public byte get(int index) {
        prepareForRead(index, 1);

        return _data[index];
    }

    public boolean getBoolean() {
        return get() != 0;
    }

    public boolean getBoolean(int index) {
        return get(index) != 0;
    }

	
	/*public char getChar() {
		// TODO Auto-generated method stub
		return 0;
	}

	
	public char getChar(int index) {
		// TODO Auto-generated method stub
		return 0;
	}*/


    public int getInt() {
        prepareForRead(4);

        int i;
        i = ((_data[_position++] & 0x00ff) << 24);
        i |= ((_data[_position++] & 0x00ff) << 16);
        i |= ((_data[_position++] & 0x00ff) << 8);
        i |= ((_data[_position++] & 0x00ff) << 0);

        return i;
    }

    /**
     * Get little endian integer
     *
     * @return
     */
    public int getLEInt() {
        prepareForRead(4);

        int i;
        i = ((_data[_position++] & 0x00ff) << 0);
        i |= ((_data[_position++] & 0x00ff) << 8);
        i |= ((_data[_position++] & 0x00ff) << 16);
        i |= ((_data[_position++] & 0x00ff) << 24);

        return i;
    }

    public int get3bytesInt() {
        prepareForRead(3);

        int i;
        i = ((_data[_position++] & 0x00ff) << 16);
        i |= ((_data[_position++] & 0x00ff) << 8);
        i |= ((_data[_position++] & 0x00ff) << 0);

        return i;
    }

    public int peekInt() {
        prepareForRead(4);

        return (((_data[_position] & 0x00ff) << 24) |
                ((_data[_position + 1] & 0x00ff) << 16) |
                ((_data[_position + 2] & 0x00ff) << 8) |
                ((_data[_position + 3] & 0x00ff) << 0));
    }

    public int peekLEInt() {
        prepareForRead(4);

        return (((_data[_position] & 0x00ff) << 0) |
                ((_data[_position + 1] & 0x00ff) << 8) |
                ((_data[_position + 2] & 0x00ff) << 16) |
                ((_data[_position + 3] & 0x00ff) << 24));
    }

    public int peek3bytesInt() {
        prepareForRead(3);

        return (((_data[_position + 0] & 0x00ff) << 16) |
                ((_data[_position + 1] & 0x00ff) << 8) |
                ((_data[_position + 2] & 0x00ff) << 0));
    }

    public int getInt(int index) {
        prepareForRead(index, 4);

        return (((_data[index] & 0x00ff) << 24) |
                ((_data[index + 1] & 0x00ff) << 16) |
                ((_data[index + 2] & 0x00ff) << 8) |
                ((_data[index + 3] & 0x00ff) << 0));
    }

    public int getLEInt(int index) {
        prepareForRead(index, 4);

        return (((_data[index] & 0x00ff) << 0) |
                ((_data[index + 1] & 0x00ff) << 8) |
                ((_data[index + 2] & 0x00ff) << 16) |
                ((_data[index + 3] & 0x00ff) << 24));
    }

    public int get3bytesInt(int index) {
        prepareForRead(index, 3);

        return (((_data[index + 0] & 0x00ff) << 16) |
                ((_data[index + 1] & 0x00ff) << 8) |
                ((_data[index + 2] & 0x00ff) << 0));
    }

    public long get5bytesLong() {
        prepareForRead(5);

        long l;
        l = (((long) _data[_position++] & 0x00ff) << 32);
        l |= (((long) _data[_position++] & 0x00ff) << 24);
        l |= (((long) _data[_position++] & 0x00ff) << 16);
        l |= (((long) _data[_position++] & 0x00ff) << 8);
        l |= (((long) _data[_position++] & 0x00ff) << 0);

        return l;
    }

    public long get6bytesLong() {
        prepareForRead(6);

        long l;
        l = (((long) _data[_position++] & 0x00ff) << 40);
        l |= (((long) _data[_position++] & 0x00ff) << 32);
        l |= (((long) _data[_position++] & 0x00ff) << 24);
        l |= (((long) _data[_position++] & 0x00ff) << 16);
        l |= (((long) _data[_position++] & 0x00ff) << 8);
        l |= (((long) _data[_position++] & 0x00ff) << 0);

        return l;
    }

    public long get7bytesLong() {
        prepareForRead(7);

        long l;
        l = (((long) _data[_position++] & 0x00ff) << 48);
        l |= (((long) _data[_position++] & 0x00ff) << 40);
        l |= (((long) _data[_position++] & 0x00ff) << 32);
        l |= (((long) _data[_position++] & 0x00ff) << 24);
        l |= (((long) _data[_position++] & 0x00ff) << 16);
        l |= (((long) _data[_position++] & 0x00ff) << 8);
        l |= (((long) _data[_position++] & 0x00ff) << 0);

        return l;
    }

    public long getLong() {
        prepareForRead(8);

        long l;
        l = (((long) _data[_position++] & 0x00ff) << 56);
        l |= (((long) _data[_position++] & 0x00ff) << 48);
        l |= (((long) _data[_position++] & 0x00ff) << 40);
        l |= (((long) _data[_position++] & 0x00ff) << 32);
        l |= (((long) _data[_position++] & 0x00ff) << 24);
        l |= (((long) _data[_position++] & 0x00ff) << 16);
        l |= (((long) _data[_position++] & 0x00ff) << 8);
        l |= (((long) _data[_position++] & 0x00ff) << 0);

        return l;
    }

    public long getLELong() {
        prepareForRead(8);

        long l;
        l = (((long) _data[_position++] & 0x00ff) << 0);
        l |= (((long) _data[_position++] & 0x00ff) << 8);
        l |= (((long) _data[_position++] & 0x00ff) << 16);
        l |= (((long) _data[_position++] & 0x00ff) << 24);
        l |= (((long) _data[_position++] & 0x00ff) << 32);
        l |= (((long) _data[_position++] & 0x00ff) << 40);
        l |= (((long) _data[_position++] & 0x00ff) << 48);
        l |= (((long) _data[_position++] & 0x00ff) << 56);

        return l;
    }

    public long peekLong() {
        prepareForRead(8);

        return ((((long) _data[_position] & 0x00ff) << 56) |
                (((long) _data[_position + 1] & 0x00ff) << 48) |
                (((long) _data[_position + 2] & 0x00ff) << 40) |
                (((long) _data[_position + 3] & 0x00ff) << 32) |
                (((long) _data[_position + 4] & 0x00ff) << 24) |
                (((long) _data[_position + 5] & 0x00ff) << 16) |
                (((long) _data[_position + 6] & 0x00ff) << 8) |
                (((long) _data[_position + 7] & 0x00ff) << 0));
    }

    public long peekLELong() {
        prepareForRead(8);

        return ((((long) _data[_position] & 0x00ff) << 0) |
                (((long) _data[_position + 1] & 0x00ff) << 8) |
                (((long) _data[_position + 2] & 0x00ff) << 16) |
                (((long) _data[_position + 3] & 0x00ff) << 24) |
                (((long) _data[_position + 4] & 0x00ff) << 32) |
                (((long) _data[_position + 5] & 0x00ff) << 40) |
                (((long) _data[_position + 6] & 0x00ff) << 48) |
                (((long) _data[_position + 7] & 0x00ff) << 56));
    }

    public long getLong(int index) {
        prepareForRead(index, 8);

        return ((((long) _data[index] & 0x00ff) << 56) |
                (((long) _data[index + 1] & 0x00ff) << 48) |
                (((long) _data[index + 2] & 0x00ff) << 40) |
                (((long) _data[index + 3] & 0x00ff) << 32) |
                (((long) _data[index + 4] & 0x00ff) << 24) |
                (((long) _data[index + 5] & 0x00ff) << 16) |
                (((long) _data[index + 6] & 0x00ff) << 8) |
                (((long) _data[index + 7] & 0x00ff) << 0));
    }

    public long getLELong(int index) {
        prepareForRead(index, 8);

        return ((((long) _data[index] & 0x00ff) << 0) |
                (((long) _data[index + 1] & 0x00ff) << 8) |
                (((long) _data[index + 2] & 0x00ff) << 16) |
                (((long) _data[index + 3] & 0x00ff) << 24) |
                (((long) _data[index + 4] & 0x00ff) << 32) |
                (((long) _data[index + 5] & 0x00ff) << 40) |
                (((long) _data[index + 6] & 0x00ff) << 48) |
                (((long) _data[index + 7] & 0x00ff) << 56));
    }

    public short getShort() {
        prepareForRead(2);

        short s;
        s = (short) ((_data[_position++] & 0x00ff) << 8);
        s |= (short) ((_data[_position++] & 0x00ff) << 0);

        return s;
    }

    public short getLEShort() {
        prepareForRead(2);

        short s;
        s = (short) ((_data[_position++] & 0x00ff) << 0);
        s |= (short) ((_data[_position++] & 0x00ff) << 8);

        return s;
    }

    public short peekShort() {
        prepareForRead(2);

        return (short) (((_data[_position] & 0x00ff) << 8) |
                ((_data[_position + 1] & 0x00ff) << 0));
    }

    public short peekLEShort() {
        prepareForRead(2);

        return (short) (((_data[_position] & 0x00ff) << 0) |
                ((_data[_position + 1] & 0x00ff) << 8));
    }

    public short getShort(int index) {
        prepareForRead(index, 2);

        return (short) (((_data[index] & 0x00ff) << 8) |
                ((_data[index + 1] & 0x00ff) << 0));
    }

    public short getLEShort(int index) {
        prepareForRead(index, 2);

        return (short) (((_data[index] & 0x00ff) << 0) |
                ((_data[index + 1] & 0x00ff) << 8));
    }

    // get bytes

    public byte[] getLastBytes() {
        prepareAllForRead();

        byte b[];

        if (_limit - _position <= 0) return new byte[0];

        b = Arrays.copyOfRange(_data, _position, _limit);
        _position = _limit;

        return b;
    }

    public byte[] getBytesWithFixedLength(int length) {
        prepareForRead(length);

        if (length == 0) return new byte[0];

        byte b[];

        b = Arrays.copyOfRange(_data, _position, _position + length);
        _position += length;

        return b;
    }

    public byte[] getBytesWithOneByteLength() {
        prepareForRead(1);

        int length = _data[_position++];

        prepareForRead(length);

        if (length == 0) return new byte[0];

        byte b[];

        b = Arrays.copyOfRange(_data, _position, _position + length);
        _position += length;

        return b;
    }

    public byte[] getBytesWithTwoBytesLength() {
        prepareForRead(2);

        int length = ((int) _data[_position++] & 0x00ff) << 8;
        length |= ((int) _data[_position++] & 0x00ff);

        prepareForRead(length);

        if (length == 0) return new byte[0];

        byte b[];

        b = Arrays.copyOfRange(_data, _position, _position + length);
        _position += length;

        return b;
    }

    // get string
    public String getLastString() {
        prepareAllForRead();

        if (_limit - _position <= 0) return "";

        String s;
        try {
            s = new String(_data, _position, _limit - _position, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported decoded", e);
            s = new String(_data, _position, _limit - _position);
        }

        _position = _limit;

        return s;
    }

    public String getStringWithFixedLength(int length) {
        prepareForRead(length);

        if (length == 0) return "";

        String s;
        try {
            s = new String(_data, _position, length, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported decoded", e);
            s = new String(_data, _position, length);
        }

        _position += length;

        return s;
    }

    public String getStringWithOneByteLength() {
        prepareForRead(1);

        int length = _data[_position++];

        prepareForRead(length);

        if (length == 0) return "";

        String s;
        try {
            s = new String(_data, _position, length, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported decoded", e);
            s = new String(_data, _position, length);
        }

        _position += length;

        return s;
    }

    public String getStringWithTwoBytesLength() {
        prepareForRead(2);

        int length = ((int) _data[_position++] & 0x00ff) << 8;
        length |= ((int) _data[_position++] & 0x00ff);

        prepareForRead(length);

        if (length == 0) return "";

        String s;
        try {
            s = new String(_data, _position, length, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported decoded", e);
            s = new String(_data, _position, length);
        }

        _position += length;

        return s;
    }

    public DyBuf put(byte b) {
        prepareForWrite(1);

        _data[_position++] = b;

        return this;
    }


    public DyBuf put(int index, byte b) {
        prepareForWrite(index, 1);

        _data[index] = b;

        return this;
    }

    public DyBuf put(boolean b) {
        return put(b ? (byte) 1 : (byte) 0);
    }

    public DyBuf put(int index, boolean b) {
        return put(index, b ? (byte) 1 : (byte) 0);
    }
	
	/*public DyBuf putChar(char value) {
		// TODO Auto-generated method stub
		return null;
	}

	
	public DyBuf putChar(int index, char value) {
		short s = (short)value;
	}*/


    public DyBuf putInt(int value) {
        prepareForWrite(4);

        _data[_position++] = (byte) ((value >> 24) & 0xff);
        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putLEInt(int value) {
        prepareForWrite(4);

        _data[_position++] = (byte) (value & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 24) & 0xff);

        return this;
    }

    public DyBuf put3bytesInt(int value) {
        prepareForWrite(3);

        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putInt(int index, int value) {
        prepareForWrite(index, 4);

        _data[index] = (byte) ((value >> 24) & 0xff);
        _data[index + 1] = (byte) ((value >> 16) & 0xff);
        _data[index + 2] = (byte) ((value >> 8) & 0xff);
        _data[index + 3] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putLEInt(int index, int value) {
        prepareForWrite(index, 4);

        _data[index++] = (byte) (value & 0xff);
        _data[index++] = (byte) ((value >> 8) & 0xff);
        _data[index++] = (byte) ((value >> 16) & 0xff);
        _data[index++] = (byte) ((value >> 24) & 0xff);

        return this;
    }

    public DyBuf put3bytesInt(int index, int value) {
        prepareForWrite(index, 3);

        _data[index + 0] = (byte) ((value >> 16) & 0xff);
        _data[index + 1] = (byte) ((value >> 8) & 0xff);
        _data[index + 2] = (byte) (value & 0xff);

        return this;
    }


    public DyBuf put5bytesLong(long value) {
        prepareForWrite(5);

        _data[_position++] = (byte) ((value >> 32) & 0xff);
        _data[_position++] = (byte) ((value >> 24) & 0xff);
        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf put6bytesLong(long value) {
        prepareForWrite(6);

        _data[_position++] = (byte) ((value >> 40) & 0xff);
        _data[_position++] = (byte) ((value >> 32) & 0xff);
        _data[_position++] = (byte) ((value >> 24) & 0xff);
        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf put7bytesLong(long value) {
        prepareForWrite(7);

        _data[_position++] = (byte) ((value >> 48) & 0xff);
        _data[_position++] = (byte) ((value >> 40) & 0xff);
        _data[_position++] = (byte) ((value >> 32) & 0xff);
        _data[_position++] = (byte) ((value >> 24) & 0xff);
        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putLong(long value) {
        prepareForWrite(8);

        _data[_position++] = (byte) ((value >> 56) & 0xff);
        _data[_position++] = (byte) ((value >> 48) & 0xff);
        _data[_position++] = (byte) ((value >> 40) & 0xff);
        _data[_position++] = (byte) ((value >> 32) & 0xff);
        _data[_position++] = (byte) ((value >> 24) & 0xff);
        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putLELong(long value) {
        prepareForWrite(8);

        _data[_position++] = (byte) (value & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) ((value >> 16) & 0xff);
        _data[_position++] = (byte) ((value >> 24) & 0xff);
        _data[_position++] = (byte) ((value >> 32) & 0xff);
        _data[_position++] = (byte) ((value >> 40) & 0xff);
        _data[_position++] = (byte) ((value >> 48) & 0xff);
        _data[_position++] = (byte) ((value >> 56) & 0xff);

        return this;
    }

    public DyBuf putLong(int index, long value) {
        prepareForWrite(index, 8);

        _data[index] = (byte) ((value >> 56) & 0xff);
        _data[index + 1] = (byte) ((value >> 48) & 0xff);
        _data[index + 2] = (byte) ((value >> 40) & 0xff);
        _data[index + 3] = (byte) ((value >> 32) & 0xff);
        _data[index + 4] = (byte) ((value >> 24) & 0xff);
        _data[index + 5] = (byte) ((value >> 16) & 0xff);
        _data[index + 6] = (byte) ((value >> 8) & 0xff);
        _data[index + 7] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putLELong(int index, long value) {
        prepareForWrite(index, 8);

        _data[index++] = (byte) (value & 0xff);
        _data[index++] = (byte) ((value >> 8) & 0xff);
        _data[index++] = (byte) ((value >> 16) & 0xff);
        _data[index++] = (byte) ((value >> 24) & 0xff);
        _data[index++] = (byte) ((value >> 32) & 0xff);
        _data[index++] = (byte) ((value >> 40) & 0xff);
        _data[index++] = (byte) ((value >> 48) & 0xff);
        _data[index++] = (byte) ((value >> 56) & 0xff);

        return this;
    }

    public DyBuf putShort(short value) {
        prepareForWrite(2);

        _data[_position++] = (byte) ((value >> 8) & 0xff);
        _data[_position++] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putLEShort(short value) {
        prepareForWrite(2);

        _data[_position++] = (byte) (value & 0xff);
        _data[_position++] = (byte) ((value >> 8) & 0xff);

        return this;
    }

    public DyBuf putShort(int index, short value) {
        prepareForWrite(index, 2);

        _data[index] = (byte) ((value >> 8) & 0xff);
        _data[index + 1] = (byte) (value & 0xff);

        return this;
    }

    public DyBuf putLEShort(int index, short value) {
        prepareForWrite(index, 2);

        _data[index] = (byte) (value & 0xff);
        _data[index + 1] = (byte) ((value >> 8) & 0xff);

        return this;
    }

    /// === DOUBLE, FLOAT
    public DyBuf putDouble(double value) {
        //return putLong(Double.doubleToLongBits(value));
        return putLong(Double.doubleToRawLongBits(value));
    }

    public DyBuf putLEDouble(double value) {
        //return putLong(Double.doubleToLongBits(value));
        return putLELong(Double.doubleToRawLongBits(value));
    }

    public DyBuf putDouble(int index, double value) {
        //return putLong(index, Double.doubleToLongBits(value));
        return putLong(index, Double.doubleToRawLongBits(value));
    }

    public DyBuf putLEDouble(int index, double value) {
        //return putLong(index, Double.doubleToLongBits(value));
        return putLELong(index, Double.doubleToRawLongBits(value));
    }

    public DyBuf putFloat(float value) {
        //return putInt(Float.floatToIntBits(value));
        return putInt(Float.floatToRawIntBits(value));
    }

    public DyBuf putLEFloat(float value) {
        //return putInt(Float.floatToIntBits(value));
        return putLEInt(Float.floatToRawIntBits(value));
    }

    public DyBuf putFloat(int index, float value) {
        //return putInt(index, Float.floatToIntBits(value));
        return putInt(index, Float.floatToRawIntBits(value));
    }

    public DyBuf putLEFloat(int index, float value) {
        //return putInt(index, Float.floatToIntBits(value));
        return putLEInt(index, Float.floatToRawIntBits(value));
    }

    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    public double getLEDouble() {
        return Double.longBitsToDouble(getLELong());
    }

    public double peekDouble() {
        return Double.longBitsToDouble(peekLong());
    }

    public double peekLEDouble() {
        return Double.longBitsToDouble(peekLELong());
    }

    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    public double getLEDouble(int index) {
        return Double.longBitsToDouble(getLELong(index));
    }

    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    public float getLEFloat() {
        return Float.intBitsToFloat(getLEInt());
    }

    public float peekFloat() {
        return Float.intBitsToFloat(peekInt());
    }

    public float peekLEFloat() {
        return Float.intBitsToFloat(peekLEInt());
    }

    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    public float getLEFloat(int index) {
        return Float.intBitsToFloat(getLEInt(index));
    }

    ///

    public DyBuf putLastBytes(byte bytes[]) {
        if (bytes == null) return this;

        int l = bytes.length;

        prepareForWrite(l);

        System.arraycopy(bytes, 0, _data, _position, bytes.length);
        _position += bytes.length;

        return this;
    }

    public DyBuf putBytes(byte bytes[], int offset, int len) {
        if (bytes == null) return this;

        prepareForWrite(len);

        System.arraycopy(bytes, offset, _data, _position, len);
        _position += len;

        return this;
    }

    public DyBuf putBytesWithOneByteLength(byte bytes[]) {
        int l = bytes == null ? 0 : bytes.length;

        if (l > 255) {
            throw new IllegalArgumentException("Put data length(" + l + ") > 255");
        }

        prepareForWrite(1 + l);

        _data[_position++] = (byte) l;

        if (l > 0) {
            System.arraycopy(bytes, 0, _data, _position, bytes.length);
            _position += bytes.length;
        }

        return this;
    }

    public DyBuf putBytesWithTwoBytesLength(byte bytes[]) {
        int l = bytes == null ? 0 : bytes.length;

        if (l > 65535) {
            throw new IllegalArgumentException("Put data length(" + l + ") > 65535");
        }

        prepareForWrite(2 + l);

        _data[_position++] = (byte) ((l >> 8) & 0xff);
        _data[_position++] = (byte) (l & 0xff);

        if (l > 0) {
            System.arraycopy(bytes, 0, _data, _position, bytes.length);
            _position += bytes.length;
        }

        return this;
    }

    public DyBuf putLastString(String s) {
        try {
            return putLastBytes(s == null ? null : s.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported encoded", e);
            return putLastBytes(s.getBytes());
        }
    }

    public DyBuf putStringWithOneByteLength(String s) {
        try {
            return putBytesWithOneByteLength(s == null ? null : s.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported encoded", e);
            return putBytesWithOneByteLength(s.getBytes());
        }
    }

    public DyBuf putStringWithTwoBytesLength(String s) {
        try {
            return putBytesWithTwoBytesLength(s == null ? null : s.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported encoded", e);
            return putBytesWithTwoBytesLength(s.getBytes());
        }
    }
	
	/*public DyBuf slice() {
		// TODO: Auto-generated method stub
		return null;
	}*/

    /**
     * From: mark, To: position
     *
     * @return
     */
    public byte[] toBytesBeforeCurrentPosition() {
        return Arrays.copyOfRange(_data, _mark, _position);
    }

    public ByteBuffer toByteBufferBeforeCurrentPosition() {
        return ByteBuffer.wrap(toBytesBeforeCurrentPosition());
    }

    public void toOutputStream(OutputStream out) throws IOException {
        out.write(_data, _mark, _position - _mark);
    }

    public void writeBytesBeforeCurrentPositionTo(DyBuf buff) {
        buff.putBytes(_data, _mark, _position);
    }

    /// ================= Variable, Non-fixed length variable =====================

    public static class Typdex {
        public Typdex() {

        }

        public Typdex(byte type, int index) {
            this.index = index;
            this.type = type & 0xFF;
        }

        public Typdex(int type, int index) {
            this.index = index;
            this.type = type;
        }

        public int index;
        public int type;
    }

    public DyBuf putTypdex(Typdex typdex) {
        if (typdex.type < 0 || typdex.index < 0) {
            throw new RuntimeException("Incorrect typdex value");
        }
        if (typdex.type <= 0x0f && typdex.index <= 0x07) {                                              // header:0x00(1bits), type:0~0x0F(4bits), index:0~0x7(3bits)
            put((byte) ((typdex.type<<3) | typdex.index));
        } else if (typdex.type <= 0x3f && typdex.index <= 0x0FF) {                                      // header:0x02(2bits), type:0~0x3F(6bits), index:0~0x00FF(8bits)
            putShort((short) (0x8000 | (typdex.type<<8) | typdex.index));
        } else if (typdex.type <= 0xff && typdex.index <= 0x1FFF) {                                     // header:0x06(3bits), type:0~0xFF(8bits), index:0~0x1FFF(13bits)
            put3bytesInt((0xC00000 | (typdex.type<<13) | typdex.index));
        } else if (typdex.type <= 0xff && typdex.index <= 0x0FFFFF) {                                   // header:0x0E(4bits), type:0~0xFF(8bits), index:0~0x0FFFFF(20bits)
            putInt((0xE0000000 | (typdex.type << 20) | typdex.index));
        } else {
            throw new RuntimeException("Type or index value is too long");
            //EXCEPTIONv(@"Type value(%lld) to long", value);
        }
        return this;
    }

    public Typdex getTypdex() {
        byte header = peek();
        Typdex typdex = new Typdex();
        if ((header&0x80)==0) {
            byte v = get();
            typdex.type = (byte)((v >> 3) & 0x0F);
            typdex.index = v & 0x07;
        } else if ((header&0x40)==0) {
            short v = getShort();
            typdex.type = (v >> 8) & 0x3F;
            typdex.index = v & 0x00FF;
        } else if ((header&0x20)==0) {
            int v = get3bytesInt();
            typdex.type = (v >> 13) & 0xFF;
            typdex.index = v & 0x1FFF;
        } else if ((header&0x10)==0) {
            int v = getInt();
            typdex.type = (v >> 20) & 0xFF;
            typdex.index = v & 0x0FFFFF;
        } else {
            throw new RuntimeException("Type or index value is too long");
        }

        return typdex;
    }

    public DyBuf putVarLong(long value) {
        if (value >= 0 && value <= 0x7F) {                                // 0 ~ 0x7F
            put((byte) value);
        } else if (value >= 0 && value <= (0x3FFF + 0x80)) {
            // (0x7F+1) ~ 0x3FFF+(0x7F+1)
            // 0x80 ~ 0x3FFF+0x80
            putShort((short) (0x8000 | (value - 0x80)));
        } else if (value >= 0 && value <= (0x1FFFFFL + 0x4080L)) {
            // (0x3FFF+(0x7F+1)+1) ~ 0x1FFFFF+(0x3FFF+(0x7F+1)+1)
            // 0x4080 ~ 0x1FFFFF + 0x4080
            put3bytesInt((int) (0xC00000 | (value - 0x4080)));
        } else if (value >= 0 && value <= (0x0FFFFFFFL + 0x204080L)) {
            // (0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1) ~ 0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)
            // 0x204080 ~ 0x0FFFFFFF + 0x204080
            putInt((int) (0xE0000000 | (value - 0x204080L)));
        } else if (value >= 0 && value <= (0x07FFFFFFFFL + 0x10204080L)) {
            // (0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1) ~ 0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1)
            // 0x10204080 ~ 0x07FFFFFFFF+0x10204080
            put5bytesLong(0xF000000000L | (value - 0x10204080L));
        } else if (value >= 0 && value <= (0x03FFFFFFFFFFL + 0x0810204080L)) {
            //  (0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1)) ~ 0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))
            // 0x0810204080 ~ 0x03FFFFFFFFFF+0x0810204080
            put6bytesLong(0xF80000000000L | (value - 0x0810204080L));
        } else if (value >= 0 && value <= (0x01FFFFFFFFFFFFL + 0x040810204080L)) {
            // (0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1) ~ 0x01FFFFFFFFFFFF+(0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1)
            // 0x040810204080 ~ 0x01FFFFFFFFFFFF+0x040810204080
            put7bytesLong(0xFC000000000000L | (value - 0x040810204080L));
        } else if (value >= 0 && value <= (0x00FFFFFFFFFFFFFFL + 0x02040810204080L)) {
            // (0x01FFFFFFFFFFFF+(0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1)) ~ 0x00FFFFFFFFFFFFFF+((0x01FFFFFFFFFFFF+(0x03FFFFFFFFFF+(0x07FFFFFFFF+(0x0FFFFFFF+(0x1FFFFF+(0x3FFF+(0x7F+1)+1)+1)+1))+1)))
            // 0x02040810204080 ~ 0x00FFFFFFFFFFFFFF+0x02040810204080
            putLong(0xFE00000000000000L | (value - 0x02040810204080L));
        } else {
            // 0x0102040810204080 ~ 0xFFFFFFFFFFFFFFFF
            put((byte) 0xFF);
            putLong(value - 0x0102040810204080L);
        }
        return this;
    }

    public long getVarLong() {
        byte b = get();
        if ((b & 0x80) == 0) {
            return (b & 0x7F);
        } else if ((b & 0x40) == 0) {
            return (((b & 0x3F) << 8) | (get() & 0x00FF)) + 0x80;
        } else if ((b & 0x20) == 0) {
            return (((b & 0x1F) << 16) | (getShort() & 0x00FFFFL)) + 0x4080L;
        } else if ((b & 0x10) == 0) {
            return (((b & 0x0F) << 24) | (get3bytesInt() & 0x00FFFFFFL)) + 0x204080L;
        } else if ((b & 0x08) == 0) {
            return (((long) (b & 0x07) << 32) | (getInt() & 0x00FFFFFFFFL)) + 0x10204080L;
        } else if ((b & 0x04) == 0) {
            return (((long) (b & 0x03) << 40) | (get5bytesLong() & 0x00FFFFFFFFFFL)) + 0x0810204080L;
        } else if ((b & 0x02) == 0) {
            return (((long) (b & 0x01) << 48) | (get6bytesLong() & 0x00FFFFFFFFFFFFL)) + 0x040810204080L;
        } else if ((b & 0x01) == 0) {
            return (get7bytesLong() & 0x00FFFFFFFFFFFFFFL) + 0x02040810204080L;
        } else {
            return getLong() + 0x0102040810204080L;
        }
    }

    public byte[] getVarLongBytes() {
        byte b = peek();
        byte bs[];
        int len;
        if ((b & 0x80) == 0) {
            len = 1;
        } else if ((b & 0x40) == 0) {
            len = 2;
        } else if ((b & 0x20) == 0) {
            len = 3;
        } else if ((b & 0x10) == 0) {
            len = 4;
        } else if ((b & 0x08) == 0) {
            len = 5;
        } else if ((b & 0x04) == 0) {
            len = 6;
        } else if ((b & 0x02) == 0) {
            len = 7;
        } else if ((b & 0x01) == 0) {
            len = 8;
        } else {
            len = 9;
        }
        prepareForRead(len);
        bs = new byte[len];
        System.arraycopy(_data, _position, bs, 0, len);
        _position += len;
        return bs;
    }

    public DyBuf putSignedVarLong(long value) {
        putVarLong(((value << 1) ^ (value >> 63)));
        return this;
    }

    public long getSignedVarLong() {
        long u = getVarLong();
        return ((u & 0x01) == 0) ? (((u >> 1) & 0x7FFFFFFFFFFFFFFFL)) : ((((u >> 1) & 0x7FFFFFFFFFFFFFFFL)) ^ 0xFFFFFFFFFFFFFFFFL);
    }

    public DyBuf putUnsignedVarLong(long value) {
        putVarLong(value);
        return this;
    }

    public long getUnsignedVarLong() {
        return getVarLong();
    }

    public DyBuf putVarLengthData(byte data[]) {
        if (data == null) {
            putVarLong(0);
        } else {
            putVarLong(data.length);
            putLastBytes(data);
        }
        return this;
    }

    public DyBuf putVarLengthData(ByteBuffer buffer) {
        int l = buffer.remaining();
        putVarLong(l);

        prepareForWrite(l);

        if (l > 0) {
            buffer.get(_data, _position, l);
            _position += l;
        }

        return this;
    }

    public byte[] getVarLengthData() {
        long length = getVarLong();
        return getBytesWithFixedLength((int) length);
    }

    public DyBuf putVarLengthString(String s) {
        if (s == null) {
            putVarLong(0);
            return this;
        }

        byte data[];

        try {
            data = s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported encoded", e);
            data = s.getBytes();
        }

        putVarLong(data.length);
        putLastBytes(data);

        return this;
    }

    public String getVarLengthString() {
        int len = (int) getVarLong();

        return getStringWithFixedLength(len);
    }

    public DyBuf putVarString(String s) {
        return putVarLengthString(s);
    }

    public String getVarString() {
        return getVarLengthString();
    }

    public DyBuf putCStringWithVarLength(String s) {
        if (s == null) {
            putVarLong(0);
            return this;
        }

        byte data[];

        try {
            data = s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported encoded", e);
            data = s.getBytes();
        }

        putVarLong(data.length + 1);
        putLastBytes(data);
        put((byte) 0);

        return this;
    }

    public String getCStringWithVarLength() {
        byte[] data = getVarLengthData();
        int len = data.length;
        if (len > 0 && data[len - 1] == 0) {
            len -= 1;
        }
        try {
            return new String(data, 0, len, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Not supported decoded", e);
            return new String(data, 0, len);
        }
    }

    public static byte[] encodeJson(Object value) {
        DyBuf buf = new DyBuf(256);
        appendJsonValue(buf, value);
        return buf.toBytesBeforeCurrentPosition();
    }

    public static Object decodeJson(byte[] data) {
        DyBuf buf = new DyBuf(data, true);
        Object value = nextJsonValue(buf);
        if (buf.remaining() != 0) {
            throw new IllegalArgumentException("trailing bytes after JSON-dybuf payload");
        }
        return value;
    }

    public static DyBuf appendJsonValue(DyBuf buf, Object value) {
        LinkedHashMap<String, LinkedHashMap<String, Integer>> dictionaries = buildJsonDictionaries(value);

        buf.putTypdex(new Typdex(TYPDEX_TYP_OBJ, 0));
        buf.putUnsignedVarLong(JSON_DYBUF_FORMAT_VERSION);
        buf.putUnsignedVarLong(dictionaries.size());
        for (Map.Entry<String, LinkedHashMap<String, Integer>> entry : dictionaries.entrySet()) {
            buf.putVarString(entry.getKey());
            buf.putUnsignedVarLong(entry.getValue().size());
            for (String key : entry.getValue().keySet()) {
                buf.putVarString(key);
            }
        }

        buf.putTypdex(new Typdex(TYPDEX_TYP_OBJ, 1));
        putJsonRecord(buf, value, "$", 0, dictionaries);
        return buf;
    }

    public static Object nextJsonValue(DyBuf buf) {
        Typdex marker = buf.getTypdex();
        if (marker.type != TYPDEX_TYP_OBJ || marker.index != 0) {
            throw new IllegalArgumentException("JSON-dybuf stream must start with TYPDEX_TYP_OBJ, 0");
        }

        long version = readJsonCount(buf, "json_dybuf_format_version");
        if (version != JSON_DYBUF_FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported JSON-dybuf format version: " + version);
        }

        int dictionaryCount = (int) readJsonCount(buf, "dictionary_count");
        LinkedHashMap<String, List<String>> dictionaries = new LinkedHashMap<>();
        for (int i = 0; i < dictionaryCount; i++) {
            String path = buf.getVarString();
            if (dictionaries.containsKey(path)) {
                throw new IllegalArgumentException("duplicate JSON dictionary path: " + path);
            }
            int keyCount = (int) readJsonCount(buf, "key_count for " + path);
            ArrayList<String> keys = new ArrayList<>();
            HashSet<String> seen = new HashSet<>();
            for (int j = 0; j < keyCount; j++) {
                String key = buf.getVarString();
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("duplicate key in JSON dictionary " + path + ": " + key);
                }
                keys.add(key);
            }
            dictionaries.put(path, keys);
        }

        marker = buf.getTypdex();
        if (marker.type != TYPDEX_TYP_OBJ || marker.index != 1) {
            throw new IllegalArgumentException("JSON-dybuf payload marker must be TYPDEX_TYP_OBJ, 1");
        }

        Typdex root = buf.getTypdex();
        return getJsonPayload(buf, root.type, root.index, "$", dictionaries);
    }

    private static LinkedHashMap<String, LinkedHashMap<String, Integer>> buildJsonDictionaries(Object value) {
        LinkedHashMap<String, LinkedHashMap<String, Integer>> dictionaries = new LinkedHashMap<>();
        visitJsonValue(value, "$", dictionaries);
        return dictionaries;
    }

    private static LinkedHashMap<String, Integer> ensureJsonDictionary(
            LinkedHashMap<String, LinkedHashMap<String, Integer>> dictionaries,
            String path
    ) {
        LinkedHashMap<String, Integer> dictionary = dictionaries.get(path);
        if (dictionary == null) {
            dictionary = new LinkedHashMap<>();
            dictionaries.put(path, dictionary);
        }
        return dictionary;
    }

    private static void visitJsonValue(
            Object value,
            String path,
            LinkedHashMap<String, LinkedHashMap<String, Integer>> dictionaries
    ) {
        if (value instanceof Map<?, ?>) {
            LinkedHashMap<String, Integer> dictionary = ensureJsonDictionary(dictionaries, path);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                String key = (String) entry.getKey();
                if (!dictionary.containsKey(key)) {
                    dictionary.put(key, dictionary.size());
                }
                visitJsonValue(entry.getValue(), path + "." + dictionary.get(key), dictionaries);
            }
            return;
        }
        if (value instanceof List<?>) {
            for (Object child : (List<?>) value) {
                visitJsonValue(child, path + ".[]", dictionaries);
            }
            return;
        }
        jsonTypdexType(value);
    }

    private static void putJsonRecord(
            DyBuf buf,
            Object value,
            String path,
            int index,
            LinkedHashMap<String, LinkedHashMap<String, Integer>> dictionaries
    ) {
        int type = jsonTypdexType(value);
        buf.putTypdex(new Typdex(type, index));

        switch (type) {
            case TYPDEX_TYP_NONE:
                return;
            case TYPDEX_TYP_BOOL:
                buf.put((Boolean) value);
                return;
            case TYPDEX_TYP_INT:
                buf.putSignedVarLong(((Number) value).longValue());
                return;
            case TYPDEX_TYP_UINT:
                buf.putUnsignedVarLong(((Number) value).longValue());
                return;
            case TYPDEX_TYP_DOUBLE:
                buf.putDouble(((Number) value).doubleValue());
                return;
            case TYPDEX_TYP_STRING:
                buf.putVarString((String) value);
                return;
            case TYPDEX_TYP_ARRAY:
                List<?> list = (List<?>) value;
                buf.putUnsignedVarLong(list.size());
                for (Object child : list) {
                    putJsonRecord(buf, child, path + ".[]", 0, dictionaries);
                }
                return;
            case TYPDEX_TYP_MAP:
                Map<?, ?> map = (Map<?, ?>) value;
                LinkedHashMap<String, Integer> dictionary = dictionaries.get(path);
                if (dictionary == null) {
                    throw new IllegalArgumentException("missing JSON dictionary for path: " + path);
                }
                buf.putUnsignedVarLong(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = (String) entry.getKey();
                    int keyIndex = dictionary.get(key);
                    putJsonRecord(buf, entry.getValue(), path + "." + keyIndex, keyIndex, dictionaries);
                }
                return;
            default:
                throw new IllegalArgumentException("unhandled JSON typdex type: " + type);
        }
    }

    private static Object getJsonPayload(
            DyBuf buf,
            int type,
            int index,
            String path,
            LinkedHashMap<String, List<String>> dictionaries
    ) {
        switch (type) {
            case TYPDEX_TYP_NONE:
                return null;
            case TYPDEX_TYP_BOOL:
                return buf.getBoolean();
            case TYPDEX_TYP_INT:
                return safeJsonNumber(buf.getSignedVarLong(), "signed integer");
            case TYPDEX_TYP_UINT:
                return safeJsonNumber(buf.getUnsignedVarLong(), "unsigned integer");
            case TYPDEX_TYP_DOUBLE:
                double value = buf.getDouble();
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("decoded JSON double must be finite");
                }
                return value;
            case TYPDEX_TYP_STRING:
                return buf.getVarString();
            case TYPDEX_TYP_ARRAY:
                int count = (int) readJsonCount(buf, "array item_count");
                ArrayList<Object> result = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Typdex child = buf.getTypdex();
                    result.add(getJsonPayload(buf, child.type, child.index, path + ".[]", dictionaries));
                }
                return result;
            case TYPDEX_TYP_MAP:
                List<String> keys = dictionaries.get(path);
                if (keys == null) {
                    throw new IllegalArgumentException("missing JSON dictionary for path: " + path);
                }
                int presentCount = (int) readJsonCount(buf, "present_count for " + path);
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                HashSet<Integer> seenIndices = new HashSet<>();
                for (int i = 0; i < presentCount; i++) {
                    Typdex child = buf.getTypdex();
                    if (!seenIndices.add(child.index)) {
                        throw new IllegalArgumentException("duplicate key index " + child.index + " in JSON object " + path);
                    }
                    if (child.index >= keys.size()) {
                        throw new IllegalArgumentException("key index " + child.index + " out of range for JSON object " + path);
                    }
                    map.put(
                            keys.get(child.index),
                            getJsonPayload(buf, child.type, child.index, path + "." + child.index, dictionaries)
                    );
                }
                return map;
            default:
                throw new IllegalArgumentException("unsupported JSON typdex type: " + type);
        }
    }

    private static int jsonTypdexType(Object value) {
        if (value == null) {
            return TYPDEX_TYP_NONE;
        }
        if (value instanceof Boolean) {
            return TYPDEX_TYP_BOOL;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long longValue = ((Number) value).longValue();
            if (longValue < MIN_JSON_SAFE_INTEGER || longValue > MAX_JSON_SAFE_INTEGER) {
                throw new IllegalArgumentException("JSON integer is outside JavaScript safe integer range");
            }
            return longValue < 0 ? TYPDEX_TYP_INT : TYPDEX_TYP_UINT;
        }
        if (value instanceof Float || value instanceof Double) {
            double doubleValue = ((Number) value).doubleValue();
            if (!Double.isFinite(doubleValue)) {
                throw new IllegalArgumentException("JSON number must be finite");
            }
            return TYPDEX_TYP_DOUBLE;
        }
        if (value instanceof String) {
            return TYPDEX_TYP_STRING;
        }
        if (value instanceof List<?>) {
            return TYPDEX_TYP_ARRAY;
        }
        if (value instanceof Map<?, ?>) {
            return TYPDEX_TYP_MAP;
        }
        throw new IllegalArgumentException("unsupported JSON value type: " + value.getClass().getName());
    }

    private static long readJsonCount(DyBuf buf, String label) {
        long value = buf.getUnsignedVarLong();
        if (Long.compareUnsigned(value, MAX_JSON_SAFE_INTEGER) > 0) {
            throw new IllegalArgumentException(label + " is too large");
        }
        return value;
    }

    private static Long safeJsonNumber(long value, String label) {
        if (value < MIN_JSON_SAFE_INTEGER || value > MAX_JSON_SAFE_INTEGER) {
            throw new IllegalArgumentException("decoded JSON " + label + " is outside JavaScript safe integer range");
        }
        return value;
    }

    /// 1 byte crc and xor
    public DyBuf put1ByteCRC() {
        checkFullDataAlgorithmSupport();

        byte crc = 0;
        for (int i = 0; i < _position; i++) {
            if ((i & 0x0f) == 0) {
                crc = (byte) ~crc;
            }
            crc ^= _data[i];
        }

        return this.put(crc);
    }

    public DyBuf applyXOR() {
        checkFullDataAlgorithmSupport();

        byte xor = (byte) 0xff;
        for (int i = _position - 1; i >= 0; i--) {
            xor ^= _data[i];
            _data[i] = xor;
        }

        return this;
    }

    public DyBuf applyXOR2() {
        checkFullDataAlgorithmSupport();

        byte xor = (byte) 0x5a;
        for (int i = _position - 1; i >= 0; i--) {
            //System.out.printf("%02x ^ %02x", xor, _data[i]);
            xor ^= _data[i];
            //System.out.printf("=%02X\n", xor);
            _data[i] = xor;
        }

        return this;
    }

    public DyBuf applyXOR3() {
        checkFullDataAlgorithmSupport();

        byte xor = (byte) 0x5a;
        for (int i = _position - 1; i >= 0; i -= 3) {
            //System.out.printf("%02x ^ %02x", xor, _data[i]);
            //xor = (byte)(xor ^ _data[i]);
            xor ^= _data[i];
            //System.out.printf("=%02X\n", xor);
            _data[i] = xor;
        }
        for (int i = _position - 2; i >= 0; i -= 3) {
            //System.out.printf("%02x ^ %02x", xor, _data[i]);
            //xor = (byte)(xor ^ _data[i]);
            xor ^= _data[i];
            //System.out.printf("=%02X\n", xor);
            _data[i] = xor;
        }
        for (int i = _position - 3; i >= 0; i -= 3) {
            //System.out.printf("%02x ^ %02x", xor, _data[i]);
            //xor = (byte)(xor ^ _data[i]);
            xor ^= _data[i];
            //System.out.printf("=%02X\n", xor);
            _data[i] = xor;
        }

        return this;
    }

    // if use, always call this after init.
    public boolean truncate1ByteCRC() {
        checkFullDataAlgorithmSupport();

        switch (_limit) {
            case 0:
                return false;
            default: {
                byte crc = 0;
                for (int i = 0; i < _limit - 1; i++) {
                    if ((i & 0x0f) == 0) {
                        crc = (byte) ~crc;
                    }
                    crc ^= _data[i];
                }
                if (_data[_limit - 1] == crc) {
                    _limit--;
                    return true;
                } else {
                    return false;
                }
            }
        }
        //return true;
    }

    public DyBuf unapplyXOR() {
        checkFullDataAlgorithmSupport();

        byte xor = (byte) 0xff, pre = 0;
        for (int i = _limit - 1; i >= 0; i--) {
            pre = _data[i];
            _data[i] ^= xor;
            xor = pre;
        }

        return this;
    }

    public DyBuf unapplyXOR2() {
        checkFullDataAlgorithmSupport();

        byte xor = (byte) 0x5a, pre = 0;
        for (int i = _limit - 1; i >= 0; i--) {
            pre = _data[i];
            _data[i] ^= xor;
            xor = pre;
        }

        return this;
    }

    /// === herlp function


    public static byte[] readAllBytes(InputStream is) {
        ByteArrayOutputStream ous = null;
        try {
            byte[] buffer = new byte[1024];
            ous = new ByteArrayOutputStream();
            int read = 0;
            try {
                while ((read = is.read(buffer)) > 0) {
                    ous.write(buffer, 0, read);
                }
            } catch (Exception e) {
                return null;
            }
        } finally {
            try {
                if (ous != null) ous.close();
            } catch (Exception e) {
                return null;
            }

            try {
                if (is != null) is.close();
            } catch (Exception e) {
                return null;
            }
        }
        return ous.toByteArray();
    }

    public static String readAllBytesAsString(InputStream is) {
        byte bs[] = readAllBytes(is);
        if (bs == null) return null;
        try {
            return new String(bs, "UTF-8");
        } catch (Exception e) {
            return new String(bs);
        }
    }

    /// === Random access file handle, use it be carefully

    /**
     * If length is large than file size, zero will be filled.
     * Data will write in current position.
     *
     * @throws IOException
     */
    DyBuf readDataFromFile(RandomAccessFile raFile, long filePosition, long length) throws IOException {
        if (_position < 0 || length > Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException();
        }

        if (_position + length > _limit) {
            limit((int) (_position + length));
        }

        int nL = 0;
        if (filePosition < raFile.length()) {
            nL = (int) Math.min(length, raFile.length() - filePosition);

            if (nL > 0) {
                raFile.seek(filePosition);
                raFile.readFully(_data, _position, nL);
            }
        }

        if (nL != length) {
            Arrays.fill(_data, (int) (_position + nL), (int) (_position + length), (byte) 0);
        }
        _position += length;

        return this;
    }

    /**
     * Dump the data from position ~ limit to file.
     *
     * @param raFile
     * @param filePosition
     * @param length
     * @return
     * @throws IOException
     */
    DyBuf dumpDataToFile(RandomAccessFile raFile, long filePosition, long length) throws IOException {
        if (_position < 0) {
            throw new IndexOutOfBoundsException();
        }

        raFile.seek(filePosition);
        raFile.write(_data, _position, (int) Math.min(_limit - _position, length));

        return this;
    }

    /**
     * @param shiftLength
     * @return
     */
    DyBuf shiftData(int shiftLength) {
        if (shiftLength > 0) {
            if (shiftLength >= _capacity) {
                // clean
            } else {
                int len = _capacity - shiftLength;//Math.min(shiftLength, _capacity-shiftLength);
                System.arraycopy(_data, 0, _data, shiftLength, len);
            }

        } else if (shiftLength < 0) {
            shiftLength = -shiftLength;
            if (shiftLength >= _capacity) {
                // clean
            } else {
                int len = _capacity - shiftLength;//Math.min(shiftLength, _capacity-shiftLength);
                System.arraycopy(_data, shiftLength, _data, 0, len);
            }


        }

        return this;
    }
}
