/*
 * Galaxy
 * Copyright (C) 2012-2013 Parallel Universe Software Co.
 * 
 * This file is part of Galaxy.
 *
 * Galaxy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 *
 * Galaxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with Galaxy. If not, see <http://www.gnu.org/licenses/>.
 */
package co.paralleluniverse.galaxy.objects;

import co.paralleluniverse.common.io.ByteBufferInputStream;
import co.paralleluniverse.common.io.Persistable;
import co.paralleluniverse.galaxy.Cache;
import co.paralleluniverse.galaxy.CacheListener;
import co.paralleluniverse.galaxy.Grid;
import co.paralleluniverse.io.serialization.Serialization;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Wraps T and implements Distributed interface
 *
 * @author eitan
 * @param <T>
 */
public class DistributedReference<T> implements CacheListener, Persistable, java.io.Serializable {
    private final long id;
    private transient volatile T obj;
    private transient volatile long version;
    private transient byte[] tmpBuffer;

    public DistributedReference(long id, T obj) {
        this.obj = obj;
        this.id = id;
        this.version = -1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + Long.toHexString(id) + " (" + version + "): " + (obj != null ? (obj.getClass().getName() + "@" + System.identityHashCode(obj)) : "null") + "]";
    }

    public T get() {
        return obj;
    }

    public long getId() {
        return id;
    }

    protected void clear() {
        this.obj = null;
    }

    @Override
    public void invalidated(Cache cache, long id) {
    }

    @Override
    public void evicted(Cache cache, long id) {
        clear();
    }

    @Override
    public void received(Cache cache, long id, long version, ByteBuffer data) {
        if (version > this.version) {
            read(data);
            this.version = version;
        }
    }

    /**
     * This method is not thread safe!
     *
     * @return
     */
    @Override
    public int size() {
//        if (obj instanceof Persistable)
//            return ((Persistable) obj).size();
//        else
        return obj != null ? getSerialized().length : 0;
    }

    /**
     * This method is not thread safe!
     */
    @Override
    public void write(ByteBuffer buffer) {
//        if (obj instanceof Persistable)
//            ((Persistable) obj).write(buffer);
//        else {
        if (obj != null)
            buffer.put(getSerialized());
        tmpBuffer = null;
//        }
    }

    byte[] getSerialized() {
        if (tmpBuffer == null)
            tmpBuffer = serialize(obj);
        return tmpBuffer;
    }

    @Override
    public void read(ByteBuffer buffer) {
//        if (obj instanceof Persistable)
//            ((Persistable) obj).read(buffer);
//        else
        /*
         * IMPORTANT:
         * We need to take care of the situation where a simple set is invoked (w/o getx beforehand).
         * The returned PUTX will call received which will overwrite the object (which we wanted to set as the value
         * for the line)
         */
        this.obj = deserialize(new ByteBufferInputStream(buffer));
    }

    protected void set(T obj) {
        this.obj = obj;
    }

    protected byte[] serialize(T obj) {
        return obj != null ? Serialization.getInstance().write(obj) : null;
    }

    protected T deserialize(InputStream is) {
        try {
            return (T) Serialization.getInstance().read(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object writeReplace() {
        return new SerializedDistributedRef(id);
    }

    protected static class SerializedDistributedRef implements java.io.Serializable {
        final long id;

        public SerializedDistributedRef(long id) {
            this.id = id;
        }

        Object readResolve() {
            try {
                return DistributedReferenceStore.getOrCreateRef(Grid.getInstance().store(), id);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
