package com.horizen.utils;

import com.horizen.block.SidechainCreationVersions;
import com.horizen.block.SidechainsVersionsManager;
import scala.Enumeration;
import scala.Predef;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.collection.immutable.Map;

import java.util.HashMap;

public abstract class AbstractSidechainsVersionsManager implements SidechainsVersionsManager {

    @Override
    public Map<ByteArrayWrapper, Enumeration.Value> getVersions(Seq<ByteArrayWrapper> sidechainIds) {
        HashMap<ByteArrayWrapper, Enumeration.Value> res = new HashMap<>();
        for (ByteArrayWrapper id : JavaConverters.seqAsJavaList(sidechainIds))
            res.put(id, getVersion(id));

        return JavaConverters.mapAsScalaMapConverter(res).asScala().toMap(Predef.conforms());
    }
}
