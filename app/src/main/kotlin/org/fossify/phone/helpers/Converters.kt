package org.fossify.phone.helpers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    private val stringType = object : TypeToken<List<String>>() {}.type

    fun jsonToStringList(value: String) = gson.fromJson<ArrayList<String>>(value, stringType)

    fun stringListToJson(list: ArrayList<String>) = gson.toJson(list)
}
