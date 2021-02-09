package com.mt.retail.mtapidemo;

public class BookItem {
    String mName;
    double  mUnitPrice;
    double  mWeightOrCount;
    double  mPrice;
    boolean mIsByWeight;

    public BookItem(String name, double unitPrice, double weightOrCount){
        this(name, unitPrice, weightOrCount, true);
    }

    public BookItem(String name, double unitPrice, double weightOrCount, boolean isByWeight){
        mName = name;
        mUnitPrice = unitPrice;
        mWeightOrCount = weightOrCount;
        mIsByWeight = isByWeight;
        mPrice  = mUnitPrice * weightOrCount;
    }

    public String getName() {
        return mName;
    }

    public double getUnitPrice() {
        return mUnitPrice;
    }

    public double getWeight() {
        return mWeightOrCount;
    }

    public int getCount() {
        return (int)mWeightOrCount;
    }
    public double getPrice() {
        return mPrice;
    }



}
