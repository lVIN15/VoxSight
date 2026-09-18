package com.cit.kaido.voxsight.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditProfileScreen(
    currentName: String,
    onBackClicked: () -> Unit,
    onSaveClicked: (newName: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    val topPurple = Color(0xFF38036B)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // --- Top App Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Edit your Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp)) // To balance the back button
        }
        
        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // --- Profile Picture Edit ---
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { /* TODO: Implement Image Picker */ },
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for when there is no image
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile Picture Placeholder",
                    tint = Color.LightGray,
                    modifier = Modifier.size(80.dp)
                )
            }
            
            // Camera Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = 50.dp, y = (-8).dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF4A5568)) // Dark grayish blue
                    .clickable { /* TODO: Implement Image Picker */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Change Photo",
                    tint = Color(0xFF4FD1C5), // Teal color
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // --- Name Input ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            com.cit.kaido.voxsight.ui.screens.auth.VoxOutlinedField(
                value = name,
                onValueChange = { name = it },
                label = "YOUR NAME",
                placeholder = "e.g. John Doe"
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // --- Save Button ---
            Button(
                onClick = { onSaveClicked(name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), spotColor = topPurple),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = topPurple)
            ) {
                Text(
                    text = "SAVE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

