package com.fjjy;

public class Util {

    public static double fastApproxSqrt(double x) {
        if (x <= 0.0) return 0.0;
        long i = Double.doubleToLongBits(x);
        i = 0x5fe6ec85e7de30daL - (i >> 1);
        double y = Double.longBitsToDouble(i);
        return x * y;
    }

    public static float fastApproxSqrt(float x) {
        if (x <= 0.0f) return 0.0f;
        int i = Float.floatToIntBits(x);
        i = 0x5f375a86 - (i >> 1);
        float y = Float.intBitsToFloat(i);
        return x * y;
    }
    
}
