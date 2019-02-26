package com.aziz.sstalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.TextView
import com.aziz.sstalk.models.Models
import com.aziz.sstalk.utils.FirebaseUtils
import com.aziz.sstalk.utils.Pref
import com.aziz.sstalk.utils.utils
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.app_bar_home.*
import kotlinx.android.synthetic.main.content_home.*
import kotlinx.android.synthetic.main.item_contact_list.view.*
import java.lang.Exception

class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    val context = this@HomeActivity

    var hasPermission:Boolean = false
    val id = R.drawable.contact_placeholder
    var isAnyMuted = false

    lateinit var adapter:FirebaseRecyclerAdapter<Models.LastMessageDetail, ViewHolder>

    val selectedItemPosition:MutableList<Int> = ArrayList()

    var actionMode:ActionMode? = null

    var isContextToolbarActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setSupportActionBar(toolbar)

        //storing firebase token, if updated
        FirebaseUtils.updateFCMToken()

        val toggle = ActionBarDrawerToggle(
            this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        show_contacts.setOnClickListener {

            startActivity(Intent(context, ContactsActivity::class.java))
        }

        nav_view.setNavigationItemSelectedListener(this)

          hasPermission = utils.hasContactPermission(this)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(!hasPermission) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 101)
                }
                else
                    setAdapter()
            }
            else
            setAdapter()




        //setting update navigation drawer
         if(FirebaseUtils.isLoggedIn()) {

             (nav_view.getHeaderView(0).findViewById(R.id.nav_header_title) as TextView).text = FirebaseAuth.getInstance().currentUser!!.displayName
             (nav_view.getHeaderView(0).findViewById(R.id.nav_header_subtitle) as TextView).text = FirebaseAuth.getInstance().currentUser!!.phoneNumber
            FirebaseUtils.loadProfileThumbnail(this, FirebaseUtils.getUid(),
                nav_view.getHeaderView(0).findViewById<CircleImageView>(R.id.drawer_profile_image_view))
        }
    }


    override fun onResume() {
        Pref.setCurrentTargetUID(this, "")
        FirebaseUtils.setMeAsOnline()
        super.onResume()
    }

    override fun onPause() {

        if(utils.isAppIsInBackground(this))
            FirebaseUtils.setMeAsOffline()
        super.onPause()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        when(requestCode){
            101 -> {
                hasPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults.isNotEmpty()

                if(hasPermission)
                    //reset the adapter
                    setAdapter()
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }



    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_my_profile -> {

                startActivity(Intent(context, EditProfile::class.java))
            }
            R.id.nav_setting -> {

                startActivity(Intent(context, SettingsActivity::class.java))
            }

            R.id.nav_share -> {

            }

        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }


    private fun setAdapter(){

        val options = FirebaseRecyclerOptions.Builder<Models.LastMessageDetail>()
            .setQuery(FirebaseUtils.ref.lastMessage(FirebaseUtils.getUid())
                    //todo dont forget to change it
                .orderByChild(FirebaseUtils.KEY_REVERSE_TIMESTAMP),Models.LastMessageDetail::class.java)
            .build()

         adapter = object : FirebaseRecyclerAdapter<Models.LastMessageDetail, ViewHolder>(options){
            override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ViewHolder = ViewHolder(layoutInflater.inflate(R.layout.item_contact_list, p0, false))

            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Models.LastMessageDetail) {

                val uid = super.getRef(position).key.toString()

                holder.name.text = uid

                FirebaseUtils.loadProfilePic(this@HomeActivity, uid, holder.pic)

                FirebaseUtils.setLastMessage(uid, holder.lastMessage, holder.deliveryTick)

                FirebaseUtils.setUserOnlineStatus(uid, holder.onlineStatus)

                FirebaseUtils.setUserDetailFromUID(this@HomeActivity, holder.name, uid, hasPermission)

                holder.messageInfo.visibility = View.VISIBLE

                holder.time.text = utils.getLocalTime(model.timeInMillis)
                holder.time.visibility = View.VISIBLE

                FirebaseUtils.setUnreadCount(uid, holder.unreadCount, holder.name, holder.lastMessage, holder.time)

                if(!isContextToolbarActive){
                    holder.checkbox.visibility = View.INVISIBLE
                    holder.checkbox.isChecked = false
                }

                holder.itemView.setOnClickListener {

                    if(isContextToolbarActive){

                        if(!selectedItemPosition.contains(position))
                        {
                            holder.checkbox.visibility = View.VISIBLE
                            holder.checkbox.isChecked = true
                            selectedItemPosition.add(position)
                        }
                        else{
                            holder.checkbox.visibility = View.INVISIBLE
                            holder.checkbox.isChecked = false
                            selectedItemPosition.remove(position)
                        }

                        actionMode!!.title = selectedItemPosition.size.toString()
                        if(selectedItemPosition.isEmpty() && actionMode!=null)
                            actionMode!!.finish()

                        return@setOnClickListener
                    }

                    startActivity(Intent(context, MessageActivity::class.java).putExtra(FirebaseUtils.KEY_UID, uid))
                }



                holder.itemView.setOnLongClickListener {

                    if(isContextToolbarActive)
                        return@setOnLongClickListener false

                    if(!selectedItemPosition.contains(position))
                    {
                        selectedItemPosition.add(position)
                    }


                    checkIfAnyMuted(adapter.getRef(position).key!!)

                    holder.checkbox.visibility = View.VISIBLE
                    holder.checkbox.isChecked = true

                    actionMode = startSupportActionMode(object : ActionMode.Callback {
                        override fun onActionItemClicked(p0: ActionMode?, p1: MenuItem?): Boolean {

                            when(p1!!.itemId){
                                R.id.action_delete_conversation -> {
                                    Log.d("HomeActivity", "onActionItemClicked: deleting pos = $selectedItemPosition")
                                    deleteSelectedConversations(selectedItemPosition.toMutableList())
                                }

                                R.id.action_mute -> {
                                  //  muteSelectedConversations()
                                }

                                R.id.action_mark_as_read -> {
                                    markAllAsRead(selectedItemPosition.toMutableList())

                                }
                            }

                            p0!!.finish()
                            return true
                        }

                        override fun onCreateActionMode(p0: ActionMode?, p1: Menu?): Boolean {

                            p0!!.menuInflater.inflate(R.menu.converstation_option_menu, p1)
                            isContextToolbarActive = true

                            //p0.menu.findItem(R.id.action_mute).isVisible = selectedItemPosition.size == 1

                            return true
                        }

                        override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean = true

                        override fun onDestroyActionMode(p0: ActionMode?) {
                            isContextToolbarActive = false

                            for(pos in selectedItemPosition)
                                adapter.notifyItemChanged(pos)

                            selectedItemPosition.clear()
                            isAnyMuted = false

                        }

                    })
                    actionMode!!.title = selectedItemPosition.size.toString()

                    true
                }




            }

        }




        conversationRecycler.layoutManager = LinearLayoutManager(context) as RecyclerView.LayoutManager?
        conversationRecycler.adapter = adapter
//        conversationRecycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        adapter.startListening()
        setonDisconnectListener()
    }





    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val name = itemView.name!!
        val lastMessage = itemView.mobile_number!!
        val pic = itemView.pic!!
        val messageInfo = itemView.messageInfoLayout!!
        val time = itemView.messageTime!!
        val unreadCount = itemView.unreadCount!!
        val onlineStatus = itemView.online_status_imageview!!
        val checkbox = itemView.contact_checkbox!!
        val deliveryTick = itemView.delivery_status_last_msg!!

    }

    override fun onDestroy() {
        try {
            adapter.stopListening()
            FirebaseUtils.setMeAsOffline()
        }
        catch (e:Exception) {}

        super.onDestroy()
    }


    private fun setonDisconnectListener(){

        FirebaseUtils.ref.userStatus(FirebaseUtils.getUid())
            .onDisconnect()
            .setValue(Models.UserActivityStatus(FirebaseUtils.VAL_OFFLINE, System.currentTimeMillis()))
    }


    private fun deleteSelectedConversations(itemPositions:MutableList<Int>) {


        AlertDialog.Builder(context)
            .setMessage("Delete these conversation(s)?")
            .setPositiveButton("Yes") { _, _ ->

                itemPositions.forEachIndexed { index, i ->
                    val conversationRef = adapter.getRef(i)
                    val targetUID = conversationRef.key
                    //delete conversation from reference
                     conversationRef.removeValue().addOnSuccessListener {

                         //delete messages after successful conversation deletion
                         FirebaseUtils.ref.getChatRef(FirebaseUtils.getUid(),
                             targetUID!!)
                             .removeValue()


                         if(index == itemPositions.lastIndex)
                             utils.toast(context, "${itemPositions.size} Conversation(s) deleted")

                     } }  }
            .setNegativeButton("No", null)
            .show()

    }



    private fun muteSelectedConversations(){
        selectedItemPosition.forEach {
            FirebaseUtils.ref.notificationMute(adapter.getRef(it).key!!)
                .setValue(!isAnyMuted)
        }
    }


    private fun markAllAsRead(itemPositions:MutableList<Int>){
        itemPositions.forEach {
            FirebaseUtils.ref.allMessageStatus( FirebaseUtils.getUid(),
                adapter.getRef(it).key!!)
                .orderByChild("read").equalTo(false)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        if(!p0.exists())
                            return

                        for(snapshot in p0.children){
                            snapshot.ref.child("read").setValue(true)
                        }
                    }
                })
        }
    }


    private fun checkIfAnyMuted(targetUID:String){

        //set switch initial value
        FirebaseUtils.ref.notificationMute(targetUID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    if(!p0.exists()) {
                        isAnyMuted = false
                        return
                    }
                    isAnyMuted = p0.getValue(Boolean::class.java)!!
                }
            })
    }

}
