package com.horizen.transaction;

import com.horizen.box.data.NoncedBoxData;

import java.util.List;

public interface OutputDataSource {
  List<? extends NoncedBoxData> getBoxData();
}
