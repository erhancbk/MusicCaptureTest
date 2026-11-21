package com.example.musiccapture
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
class MainActivity:Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(40,70,40,40) }
  val message=TextView(this).apply { text="Ses Test v1\nKurulum kontrolü";textSize=22f }
  layout.addView(message)
  layout.addView(Button(this).apply { text="TEST ET";setOnClickListener { message.text="Başarılı! Uygulama çalışıyor." } })
  setContentView(layout)
 }
}