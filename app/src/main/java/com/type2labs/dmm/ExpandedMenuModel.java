package com.type2labs.dmm;

/**
 * Created by Thomas M. Klapwijk on 28/04/2017.
 */

public class ExpandedMenuModel {

    String iconName = "";
    int iconImg = -1; // menu icon resource id

    public int getIconImg() {
        return iconImg;
    }

    public void setIconImg(int iconImg) {
        this.iconImg = iconImg;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }
}