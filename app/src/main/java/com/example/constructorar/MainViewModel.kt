package com.example.constructorar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream

class MainViewModel(application: Application): AndroidViewModel(application) {

    var currStep = 0

    var maxSteps = 0

    private var instructions: List<InstructionModel>? = null
    set(value) {
        field = value
        (instruction as MutableLiveData).postValue(value?.get(currStep))
    }
    val instruction: LiveData<InstructionModel> = MutableLiveData()

    init {
        loadInstruction()
    }

    fun nextStep(){
        currStep++
        (instruction as MutableLiveData).postValue(instructions?.get(currStep))
    }

    private fun loadInstruction(){
            val gson = Gson()
            val listInstructionType = object : TypeToken<List<InstructionModel>>() {}.type
            val jsonString = getAssetJsonData() ?: ""
            val instruction: List<InstructionModel> = gson.fromJson(jsonString, listInstructionType)
            instruction.also {
                instructions = it
                maxSteps = it.size
            }
    }

    fun getAssetJsonData(): String? {
        val json: String
        try {
            json = getApplication<Application>().assets.open("Instruction.json").bufferedReader().use { it.readText() }
        }catch (e: IOException){
            e.printStackTrace()
            return null
        }
        return json
    }
}