package com.evsct.app.data.db

import androidx.room.TypeConverter
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PaymentMethod
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

    @TypeConverter
    fun paymentMethodToString(value: PaymentMethod?): String? = value?.name

    /** Lenient, unlike the two above: an unknown name (a row written by a
     *  newer build, then the app downgraded) reads as "not recorded"
     *  instead of throwing on every query that touches the table. */
    @TypeConverter
    fun stringToPaymentMethod(value: String?): PaymentMethod? =
        value?.let { name -> PaymentMethod.entries.firstOrNull { it.name == name } }
}
