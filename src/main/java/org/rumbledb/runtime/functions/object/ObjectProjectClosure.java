package org.rumbledb.runtime.functions.object;

import org.apache.spark.api.java.function.FlatMapFunction;
import org.rumbledb.api.Item;
import org.rumbledb.exceptions.ExceptionMetadata;
import org.rumbledb.items.ItemFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public class ObjectProjectClosure implements FlatMapFunction<Item, Item> {

    private static final long serialVersionUID = 1L;
    private List<Item> projectionKeys;
    private ExceptionMetadata itemMetadata;

    public ObjectProjectClosure(List<Item> projectionKeys, ExceptionMetadata itemMetadata) {
        this.projectionKeys = projectionKeys;
        this.itemMetadata = itemMetadata;
    }

    public Iterator<Item> call(Item arg0) throws Exception {
        List<Item> results = new ArrayList<>();
        LinkedHashMap<String, Item> content = new LinkedHashMap<>();

        if (!arg0.isObject()) {
            results.add(arg0);
            return results.iterator();
        }

        for (String key : arg0.getKeys()) {
            if (this.projectionKeys.contains(new StringItem(key))) {
                content.put(key, arg0.getItemByKey(key));
            }
        }

        results.add(ItemFactory.getInstance().createObjectItem(content, this.itemMetadata));
        return results.iterator();
    }
};
