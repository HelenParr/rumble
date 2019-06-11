/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Stefan Irimescu, Can Berker Cikis
 *
 */

package sparksoniq.spark.udf;

import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.StructType;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import scala.collection.mutable.WrappedArray;
import sparksoniq.jsoniq.item.Item;
import sparksoniq.jsoniq.runtime.iterator.RuntimeIterator;
import sparksoniq.semantics.DynamicContext;
import sparksoniq.spark.DataFrameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LetClauseUDF implements UDF1<WrappedArray, List<byte[]>> {
    private RuntimeIterator _expression;
    private StructType _inputSchema;
    Set<String> _dependencies;
    String[] _columnNames;

    private DynamicContext _context;
    private List<Item> _nextResult;
    
    private transient Kryo _kryo;
    private transient Output _output;
    private transient Input _input;
    
    public LetClauseUDF(
            RuntimeIterator expression,
            StructType inputSchema) {
        _expression = expression;
        _inputSchema = inputSchema;

        _context = new DynamicContext();
        _nextResult = new ArrayList<>();
        
        _kryo = new Kryo();
        DataFrameUtils.registerKryoClassesKryo(_kryo);
        _output = new ByteBufferOutput(128, -1);
        _input = new Input();
        
        _columnNames = _inputSchema.fieldNames();
        _dependencies = _expression.getVariableDependencies();
    }


    @Override
    public List<byte[]> call(WrappedArray wrappedParameters) {
        _context.removeAllVariables();
        _nextResult.clear();
        
        Object[] serializedParams = (Object[]) wrappedParameters.array();

        // prepare dynamic context
        for (int columnIndex = 0; columnIndex < _columnNames.length; columnIndex++) {
            String var = _columnNames[columnIndex];
            if(_dependencies.contains(var))
            {
                List<Item> sequence = new ArrayList<Item>();
                List<byte[]> bytes = (List<byte[]>) serializedParams[columnIndex];
                for (byte[] b : bytes)
                {
                    Item i = (Item) DataFrameUtils.deserializeByteArray(b, _kryo, _input);
                    sequence.add(i);
                }
                _context.addVariableValue(var, sequence);
            }
        }

        // apply expression in the dynamic context
        _expression.open(_context);
        while (_expression.hasNext()) {
            Item nextItem = _expression.next();
            _nextResult.add(nextItem);
        }
        _expression.close();

        return DataFrameUtils.serializeItemList(_nextResult, _kryo, _output);
    }
    
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        
        _kryo = new Kryo();
        DataFrameUtils.registerKryoClassesKryo(_kryo);
        _output = new ByteBufferOutput(128, -1);
        _input = new Input();
    }
    
}
