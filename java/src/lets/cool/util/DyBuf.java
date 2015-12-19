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

package lets.cool.util;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DyBuf {
    static Logger log = Logger.getLogger(DyBuf.class.getName());


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
        System.arraycopy(_data, 0, newData, 0, _capacity);
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

        if (_limit - _position <= 0) return null;

        b = Arrays.copyOfRange(_data, _position, _limit);
        _position = _limit;

        return b;
    }

    public byte[] getBytesWithFixedLength(int length) {
        if (length == 0) return null;

        prepareForRead(length);

        byte b[];

        b = Arrays.copyOfRange(_data, _position, _position + length);
        _position += length;

        return b;
    }

    public byte[] getBytesWithOneByteLength() {
        prepareForRead(1);

        int length = _data[_position++];

        if (length == 0) return null;

        prepareForRead(length);

        byte b[];

        b = Arrays.copyOfRange(_data, _position, _position + length);
        _position += length;

        return b;
    }

    public byte[] getBytesWithTwoBytesLength() {
        prepareForRead(2);

        int length = ((int) _data[_position++] & 0x00ff) << 8;
        length |= ((int) _data[_position++] & 0x00ff);

        if (length == 0) return null;

        prepareForRead(length);

        byte b[];

        b = Arrays.copyOfRange(_data, _position, _position + length);
        _position += length;

        return b;
    }

    // get string
    public String getLastString() {
        prepareAllForRead();

        if (_limit - _position <= 0) return null;

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
        if (length <= 0) return null;

        prepareForRead(length);

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

        if (length == 0) return null;

        prepareForRead(length);

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

        if (length == 0) return null;

        prepareForRead(length);

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

    public static class IndexAnd4bitsType {
        public IndexAnd4bitsType() {

        }

        public IndexAnd4bitsType(long v, byte t) {
            value = v;
            RPCValueType = t;
        }

        public long value;
        public byte RPCValueType;
    }

    public DyBuf putIndexAnd4bitsType(IndexAnd4bitsType vat) {
        if (vat.value >= 0 && vat.value <= 0x07) {                                                    // 0 ~ 0x07
            put((byte) ((vat.RPCValueType << 4) | vat.value));
        } else if (vat.value >= 0 && vat.value <= 0x03FF + (0x08)) {                                    // 0x08 ~ 0x3FF+0x08
            putShort((short) ((vat.RPCValueType << 12) | 0x0800 | (vat.value - 0x08)));
        } else if (vat.value >= 0 && vat.value <= 0x01FFFFL + 0x0408L) {                                    // 0x0408 ~ 0x01FFFF+0x0408
            put3bytesInt((int) ((vat.RPCValueType << 20) | 0x0C0000 | (vat.value - 0x0408L)));
        } else if (vat.value >= 0 && vat.value <= 0x00FFFFFFL + 0x020408L) {                                // 0x020408 ~ 0x00FFFFFF+0x020408
            putInt((int) ((vat.RPCValueType << 28) | 0x0E000000L | (vat.value - 0x020408L)));
        } else {
            //throw new RuntimeException("Type value(%lld) to long", value);
            //EXCEPTIONv(@"Type value(%lld) to long", value);
        }
        return this;
    }

    public IndexAnd4bitsType getIndexAnd4bitsType() {
        byte t = get();
        IndexAnd4bitsType vat = new IndexAnd4bitsType();
        vat.RPCValueType = (byte) ((t >> 4) & 0x0F);
        if ((t & 0x08) == 0) {
            vat.value = t & 0x07;
        } else if ((t & 0x04) == 0) {
            vat.value = (((t & 0x03) << 8) | (get() & 0x00FF)) + 0x08;
        } else if ((t & 0x02) == 0) {
            vat.value = (((t & 0x01) << 16) | (getShort() & 0x00FFFFL)) + 0x0408;
        } else if ((t & 0x01) == 0) {
            vat.value = (((t & 0x00) << 24) | (get3bytesInt() & 0x00FFFFFFL)) + 0x020408L;
        } else {
            //EXCEPTIONv(@"Type value(%d) to long", vat.type);
        }

        return vat;

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



