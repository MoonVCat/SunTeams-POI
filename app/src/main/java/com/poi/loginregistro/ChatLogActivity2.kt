package com.poi.loginregistro

import android.app.Dialog
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.poi.loginregistro.Modelos.ChatMessage
import com.poi.loginregistro.Modelos.users
import com.poi.loginregistro.helpers.General
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_chat_log2.*
import kotlinx.android.synthetic.main.agregar_archivo.*
import kotlinx.android.synthetic.main.chat_from_row.view.*
import kotlinx.android.synthetic.main.chat_to_row.view.*
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChatLogActivity2 : AppCompatActivity() {

    companion object{
        val TAG = "ChatLog"
        var currentUser: users? = null
        val USER_KEY = "USER_KEY"
    }

    val adapter = GroupAdapter<GroupieViewHolder>()
    private var uid :String=""
    private var name : String= ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_log2)

        recyclerViewChatLog.adapter = adapter


        //val username = intent.getStringExtra(NewMessageActivity.USER_KEY)
        val user = intent.getParcelableExtra<users>(NewMessageActivity.USER_KEY)
        supportActionBar?.title = user?.username

        //setupDummyData()

        listenFromMessages()


        btnSendChat.setOnClickListener {
            Log.d(TAG, "Attempt to send message...")
            performSendMessage()
        }


    }

    private fun listenFromMessages(){

        val fromId = FirebaseAuth.getInstance().uid
        val user = intent.getParcelableExtra<users>(NewMessageActivity.USER_KEY)
        val toId = user?.uid
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId")

        ref.addChildEventListener(object: ChildEventListener{

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {

                val chatMessage = snapshot.getValue(ChatMessage::class.java)


                if (chatMessage != null) {

                    Log.d(TAG, chatMessage.text)

                    if (chatMessage.fromId == FirebaseAuth.getInstance().uid){

                        adapter.add(ChatToItem(chatMessage.text))

                    }
                    else {
                        adapter.add(ChatFromItem(chatMessage.text))
                    }

                }

            }

            override fun onCancelled(error: DatabaseError) {


            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {


            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {


            }

            override fun onChildRemoved(snapshot: DataSnapshot) {


            }

        })
    }


    private fun performSendMessage(){

        val uid = FirebaseAuth.getInstance().uid
        val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
        ref.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                //send message to firebase

                SettingsActivity.currentUser = snapshot.getValue(users::class.java)

                val users = SettingsActivity.currentUser?.encrypted

                if (users.toString().equals("activated")){

                    val text = textSendChat.text.toString()

                    val mensajeCifrado = CifradoUtils.cifrar(text, "Yoshikage")


                    val fromId = FirebaseAuth.getInstance().uid
                    val user = intent.getParcelableExtra<users>(NewMessageActivity.USER_KEY)
                    val toId = user?.uid

                    if (fromId == null) return

                    //val reference = FirebaseDatabase.getInstance().getReference("/messages").push()
                    val reference = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId").push()

                   val toReference = FirebaseDatabase.getInstance().getReference("/user-messages/$toId/$fromId").push()

                    val chatMessage = ChatMessage(reference.key!!, mensajeCifrado, fromId, toId.toString(), "", "", "", ServerValue.TIMESTAMP )


                    reference.setValue(chatMessage)
                        .addOnSuccessListener {
                            Log.d(TAG, "Saved message: ${reference.key}")
                            textSendChat.text.clear()
                            recyclerViewChatLog.scrollToPosition(adapter.itemCount -1)
                        }

                    toReference.setValue(chatMessage)

                }
                else{
                    val text = textSendChat.text.toString()

                    val fromId = FirebaseAuth.getInstance().uid
                    val user = intent.getParcelableExtra<users>(NewMessageActivity.USER_KEY)
                    val toId = user?.uid

                    val mensajeCifrado = CifradoUtils.cifrar(text, "Yoshikage")

                    val mensajeDescifrado = CifradoUtils.descifrar(mensajeCifrado, "Yoshikage")

                    if (fromId == null) return

                    //val reference = FirebaseDatabase.getInstance().getReference("/messages").push()
                    val reference = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId").push()

                    val toReference = FirebaseDatabase.getInstance().getReference("/user-messages/$toId/$fromId").push()

                    val chatMessage = ChatMessage(reference.key!!, mensajeDescifrado, fromId, toId.toString(), "","", "", ServerValue.TIMESTAMP )

                    reference.setValue(chatMessage)
                        .addOnSuccessListener {
                            Log.d(TAG, "Saved message: ${reference.key}")
                            textSendChat.text.clear()
                            recyclerViewChatLog.scrollToPosition(adapter.itemCount -1)
                        }

                    toReference.setValue(chatMessage)


                }

            }

            override fun onCancelled(error: DatabaseError) {

            }

        })


    }

    object CifradoUtils{

        private const val CIPHER_TRANSFORM = ("AES/CBC/PKCS5Padding")

        fun cifrar (textoPlano: String, llaveSecreta: String): String {

            val cipher = Cipher.getInstance(CIPHER_TRANSFORM)

            val llavesBytesFinal = ByteArray(16)
            val llavesBytesOriginal = llaveSecreta.toByteArray(charset("UTF-8"))

            System.arraycopy(
                llavesBytesOriginal, 0, llavesBytesFinal, 0,
                Math.min(llavesBytesOriginal.size, llavesBytesFinal.size)
            )

            val secretKeySpec = SecretKeySpec(llavesBytesFinal, "AES")

            val initVector = IvParameterSpec(llavesBytesFinal)

            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKeySpec,
                initVector
            )

            val textoCifrado = cipher.doFinal(textoPlano.toByteArray(charset("UTF-8")))

            val resultadoBase64 = String(Base64.encode(textoCifrado, Base64.NO_PADDING))

            return resultadoBase64
        }

        fun descifrar(textoCifrado: String, llaveSecreta: String) : String {

            val textoCifradoBytes = Base64.decode(textoCifrado, Base64.NO_PADDING)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORM)

            val llavesBytesFinal = ByteArray(16)
            val llavesBytesOriginal = llaveSecreta.toByteArray(charset("UTF-8"))

            System.arraycopy(
                llavesBytesOriginal, 0, llavesBytesFinal, 0,
                Math.min(llavesBytesOriginal.size, llavesBytesFinal.size)
            )

            val secretKeySpec = SecretKeySpec(llavesBytesFinal, "AES")

            val initVector = IvParameterSpec(llavesBytesFinal)

            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKeySpec,
                initVector
            )

            val textPlanoRec = String (cipher.doFinal(textoCifradoBytes))

            return textPlanoRec
        }

    }

    /*private fun setupDummyData(){
        val adapter = GroupAdapter<GroupieViewHolder>()

        adapter.add(ChatFromItem("FROM MESSAGE"))
        adapter.add(ChatToItem("TO MESSAGE\nTOMESSAGE"))

        recyclerViewChatLog.adapter = adapter

    }*/
}

class ChatFromItem(val text: String): Item<GroupieViewHolder>(){

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {

        viewHolder.itemView.textView_from.text = text

    }

    override fun getLayout(): Int {

        return R.layout.chat_from_row
    }

}

class ChatToItem(val text: String): Item<GroupieViewHolder>(){

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {

        viewHolder.itemView.textView_to.text = text
    }

    override fun getLayout(): Int {

        return R.layout.chat_to_row
    }

}