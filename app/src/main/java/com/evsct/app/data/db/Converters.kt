package com.evsct.app.data.db

import androidx.room.TypeConverter
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel

class Converters {
    @TypeConverter
    fun chargingTypeToString(value: ChargingType): String = value.name

    @TypeConverter
    fun stringToChargingType(value: String): ChargingType = ChargingType.valueOf(value)

    @TypeConverter
    fun pricingModelToString(value: PricingModel): String = value.name

    @TypeConverter
    fun stringToPricingModel(value: String): PricingModel = PricingModel.valueOf(value)
}
