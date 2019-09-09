package com.horizen.api.http;

import com.horizen.node.SidechainNodeView;
import scala.util.Try;

public interface ApplicationNodeViewProvider {

    public Try<SidechainNodeView> getSidechainNodeView();

    public String serialize(Object anObject);

    public String serialize(Object anObject, Class<?> aView);

    public String serialize(Object anObject, SidechainJsonSerializer sidechainJsonSerializer);

    public String serialize(Object anObject, Class<?> aView, SidechainJsonSerializer sidechainJsonSerializer);

    public String serializeError(String code, String description, String detail);

    public String serializeError(String code, String description, String detail, Class<?> aView);

    public String serializeError(String code, String description, String detail, SidechainJsonSerializer sidechainJsonSerializer);

    public String serializeError(String code, String description, String detail, Class<?> aView, SidechainJsonSerializer sidechainJsonSerializer);

    public SidechainJsonSerializer newJsonSerializer();

}
