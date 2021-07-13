package com.example.constructorar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream

class MainViewModel(application: Application): AndroidViewModel(application) {

    var currStep = 0

    var maxSteps = 0

    private lateinit var instructions: List<InstructionModel>

    private val _instruction: MutableStateFlow<InstructionModel?> = MutableStateFlow(null)

     val instruction: StateFlow<InstructionModel?> = _instruction

    init {
        loadInstruction()
    }

    fun nextStep(){
        _instruction.value = instructions[++currStep]
    }

    private fun loadInstruction(){

            val gson = Gson()
            val listInstructionType = object : TypeToken<List<InstructionModel>>() {}.type
            val jsonString = getAssetJsonData()
            val instruction: List<InstructionModel> = gson.fromJson(jsonString, listInstructionType)
            instructions = instruction
            maxSteps = instruction.size
            _instruction.value = instructions[currStep]
    }

    fun getAssetJsonData(): String? {
        return try {
            getApplication<Application>().assets.open("Instruction.json").bufferedReader().use { it.readText() }
        }catch (e: IOException){
            e.printStackTrace()
            return null
        }
    }
}