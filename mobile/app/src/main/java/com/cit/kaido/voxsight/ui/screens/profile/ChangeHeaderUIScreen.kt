package com.cit.kaido.voxsight.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.R

@Composable
fun ChangeHeaderUIScreen(
    onBackClicked: () -> Unit,
    currentSelection: String = "default",
    onSelectionChanged: (String) -> Unit
) {
    var selectedOption by remember { mutableStateOf(currentSelection) }
    
    val topPurple = Color(0xFF38036B)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA)) // Light off-white background
    ) {
        // --- Top App Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
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
                text = "Change Header UI",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp)) // To balance the back button
        }
        
        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
        
        // --- Content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HeaderUICard(
                title = "Default",
                description = "Choose a voice part for both Audio and Visual focus.",
                imageResId = R.drawable.header_ui_default, // Provide the actual drawable ID when ready
                isSelected = selectedOption == "default",
                onClick = {
                    selectedOption = "default"
                    onSelectionChanged("default")
                },
                selectedColor = topPurple
            )
            
            HeaderUICard(
                title = "Drop Down",
                description = "Individually choose a voice part for Audio and Visual focus.",
                imageResId = R.drawable.header_ui_dropdown, // Provide the actual drawable ID when ready
                isSelected = selectedOption == "dropdown",
                onClick = {
                    selectedOption = "dropdown"
                    onSelectionChanged("dropdown")
                },
                selectedColor = topPurple
            )
        }
    }
}

@Composable
fun HeaderUICard(
    title: String,
    description: String,
    imageResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color
) {
    val borderColor = if (isSelected) Color(0xFFE0E0E0) else Color(0xFFEEEEEE)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color.Black,
                        unselectedColor = Color.Gray
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Image(
                painter = painterResource(id = imageResId),
                contentDescription = "$title UI preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.DarkGray,
                lineHeight = 20.sp
            )
        }
    }
}

