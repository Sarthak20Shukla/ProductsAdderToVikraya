package com.example.productsaddertovikraya

import android.app.Activity
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.productsaddertovikraya.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var selectedImages= mutableListOf<Uri>()
    private val selectedColors= mutableListOf<Int>()
    private val firestore = Firebase.firestore
    private val productsStorage= Firebase.storage.reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.buttonColorPicker.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Product Color")
                .setPositiveButton("Select",object :ColorEnvelopeListener{
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }

                })
                .setNegativeButton("Cancel"){
                    colorPicker,_-> colorPicker.dismiss()
                }.show()
        }

        val selectImagesActivityResult=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result -> if(result.resultCode == Activity.RESULT_OK){
                val intent=result.data
            if(intent?.clipData !=null){
                val count=intent.clipData?.itemCount ?:0
                (0 until count).forEach {
                    val imageUri=intent.clipData?.getItemAt(it)?.uri
                    imageUri?.let{
                        selectedImages.add(it)
                    }
                }
            } else{
                val imageUri=intent?.data
                imageUri?.let {selectedImages.add(it)}
            }
            updateImages()
        }
        }
        binding.buttonImagesPicker.setOnClickListener{
            val intent= Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            intent.type="image/*"
            selectImagesActivityResult.launch(intent)
        }
    }

    private fun updateImages() {
        binding.tvSelectedImages.text=selectedImages.size.toString()
    }

    private fun updateColors() {
        var colors=""
        selectedColors.forEach {
            colors="$colors ${Integer.toHexString(it)}"
        }
        binding.tvSelectedColors.text=colors
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    private fun showLoading() {
        binding.progressbar.visibility= View.VISIBLE
    }

    private fun saveProducts() {
        val name=binding.edName.text.toString().trim()
        val category=binding.edCategory.text.toString().trim()
        val price=binding.edPrice.text.toString().trim()
        val offerPercentage=binding.offerPercentage.text.toString().trim()
        val description=binding.edDescription.text.toString().trim()
        val sizes=getSizesList(binding.edSizes.text.toString().trim())
        val imagesByteArrays=getImagesByteArrays()
        val images= mutableListOf<String>()

        lifecycleScope.launch(Dispatchers.IO){
            withContext(Dispatchers.Main){
                showLoading()
            }

            try {
                async {
                    Log.d("test1", "test")
                    imagesByteArrays.forEach {
                        val id=UUID.randomUUID().toString()
                        launch {
                            val imageStorage=productsStorage.child("products/images/$id")
                            val result= imageStorage.putBytes(it).await()
                            val downloadUrl=result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()
            } catch(e: java.lang.Exception){
                e.printStackTrace()
                withContext(Dispatchers.Main){
                    hideLoading()
                }
            }
            val product=Products(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if(offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                if(description.isEmpty()) null else description,
                if(selectedImages.isEmpty()) null else selectedColors,
                sizes,
                images



                )
            firestore.collection("Products").add(product).addOnSuccessListener {
                hideLoading()
            }.addOnFailureListener{
                hideLoading()
                Log.e("Error",it.message.toString())
            }
        }
    }

    private fun hideLoading() {
        binding.progressbar.visibility= View.INVISIBLE
    }

    private fun getImagesByteArrays(): List<ByteArray> {
        val imagesByteArray= mutableListOf<ByteArray>()
        selectedImages.forEach {
            val stream= ByteArrayOutputStream()
            val imageBmp=MediaStore.Images.Media.getBitmap(this.contentResolver,it)
            if(imageBmp.compress(Bitmap.CompressFormat.JPEG,100,stream)){
                imagesByteArray.add(stream.toByteArray())
            }
        }
        return imagesByteArray
    }

    private fun getSizesList(sizesStr: String): List<String>? {
        if(sizesStr.isEmpty())
            return null
        val sizesList=sizesStr.split(",")
        return sizesList
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.saveProduct){
            val productValidation = validateInformation()
            if(!productValidation){
                Toast.makeText(this,"Check your inputs",Toast.LENGTH_SHORT).show()
                return false
            }
            saveProducts()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun validateInformation(): Boolean {
        if (binding.edPrice.text.toString().trim().isEmpty()) return false
        if (binding.edName.text.toString().trim().isEmpty()) return false
        if (binding.edCategory.text.toString().trim().isEmpty()) return false
        if (selectedImages.isEmpty()) return false
        return true
    }
}