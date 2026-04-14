package com.zm.plantdisease.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.zm.plantdisease.R
import com.zm.plantdisease.data.firebase.FirebaseManager
import com.zm.plantdisease.databinding.ActivityMainBinding
import com.zm.plantdisease.ui.auth.LoginActivity
import com.zm.plantdisease.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // التحقق من تسجيل الدخول
        if (!FirebaseManager.isLoggedIn) {
            goToLogin(); return
        }

        setupNavigation()
        observeAuthState()
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)
    }

    private fun observeAuthState() {
        vm.authState.observe(this) { user ->
            if (user == null) goToLogin()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
