package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.box.data.NoncedBoxData;
import com.horizen.proposition.Proposition;

import java.util.Collections;
import java.util.List;

//just convert Box Data to dummy BoxDataSource
public class BoxDataSource<P extends Proposition> implements OutputDataSource
{
  NoncedBoxData<Proposition, NoncedBox<Proposition>> boxData;

  public BoxDataSource(NoncedBoxData<Proposition, NoncedBox<Proposition>> boxData){
    this.boxData = boxData;
  }

  @Override
  public List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> getBoxData()
  {
    return Collections.singletonList(boxData);
  }
}
