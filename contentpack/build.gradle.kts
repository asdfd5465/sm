plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("contentpack") 
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
