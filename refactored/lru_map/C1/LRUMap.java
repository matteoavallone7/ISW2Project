/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openjpa.lib.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * Extension of the commons LRUMap that can change its
 * maximum size.
 *
 * @author Abe White
 */
public class LRUMap extends org.apache.openjpa.lib.util.collections.LRUMap
    implements SizedMap {

    private static final long serialVersionUID = 1L;
    private static final int USE_DEFAULT_MAX_SIZE = -1;

    private int configuredMaxSize = USE_DEFAULT_MAX_SIZE;

    public LRUMap() {
        super();
    }

    public LRUMap(final int initCapacity) {
        super(initCapacity);
    }

    public LRUMap(final int initCapacity, final float loadFactor) {
        super(initCapacity, loadFactor);
    }

    public LRUMap(final Map map) {
        super(map);
    }

    @Override
    public int getMaxSize() {
        return maxSize();
    }

    @Override
    public void setMaxSize(final int max) {
        if (max < 0) {
            throw new IllegalArgumentException(String.valueOf(max));
        }
        configuredMaxSize = max;
    }

    @Override
    public void overflowRemoved(final Object key, final Object value) {
    }

    @Override
    public int maxSize() {
        return hasConfiguredMaxSize() ? configuredMaxSize : super.maxSize();
    }

    @Override
    public boolean isFull() {
        return hasConfiguredMaxSize()
            ? size() >= configuredMaxSize
            : super.isFull();
    }

    private boolean hasConfiguredMaxSize() {
        return configuredMaxSize != USE_DEFAULT_MAX_SIZE;
    }

    @Override
    protected boolean removeLRU(final LinkEntry entry) {
        overflowRemoved(entry.getKey(), entry.getValue());
        return super.removeLRU(entry);
    }

    @Override
    protected void doWriteObject(final ObjectOutputStream output)
        throws IOException {
        output.writeInt(configuredMaxSize);
        super.doWriteObject(output);
    }

    @Override
    protected void doReadObject(final ObjectInputStream input)
        throws IOException, ClassNotFoundException {
        configuredMaxSize = input.readInt();
        super.doReadObject(input);
    }
}
