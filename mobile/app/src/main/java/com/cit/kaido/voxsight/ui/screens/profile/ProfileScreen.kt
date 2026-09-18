package com.cit.kaido.voxsight.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Lucide

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ProfileScreen(
    username: String,
    onBackClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    onUpgradeToPremiumClicked: () -> Unit = {},
    onSettingsClicked: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val topPurple = Color(0xFF38036B)
    val lightPurple = Color(0xFFEFE8F5)
    val orangeDark = Color(0xFFFF9800)
    val redDark = Color(0xFFD32F2F)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoxBackground)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // --- Top Purple Section ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(topPurple)
                .padding(top = 48.dp, bottom = 48.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                // Close Button Row (X on top right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onBackClicked,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Profile Info Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Picture",
                            tint = Color.LightGray,
                            modifier = Modifier.size(60.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column {
                        Text(
                            text = username,
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FREE MEMBER",
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { /* Edit Profile Action */ }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit Profile",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EDIT YOUR PROFILE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Streak Calendar ---
        StreakCalendar()

        Spacer(modifier = Modifier.height(32.dp))

        // --- Action List ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ProfileCard(
                title = "Upgrade to Premium",
                icon = Lucide.Crown,
                iconColor = orangeDark,
                onClick = onUpgradeToPremiumClicked
            )

            ProfileCard(
                title = "Settings",
                icon = Icons.Outlined.Settings,
                iconColor = topPurple,
                onClick = onSettingsClicked
            )

            ProfileCard(
                title = "Log out",
                titleColor = redDark,
                icon = Icons.AutoMirrored.Outlined.Logout,
                iconColor = redDark,
                showArrow = false,
                onClick = { showLogoutDialog = true }
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = "Log out", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to log out?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClicked()
                    }
                ) {
                    Text("Yes", color = redDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun StreakCalendar() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentYearMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
    val today = java.time.LocalDate.now()

    // Fetch actual streak dates from SharedPreferences
    val streakDates = remember(currentYearMonth) {
        com.cit.kaido.voxsight.util.StreakManager.getStreakDates(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Your Test Pitch Streak",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Calendar Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { currentYearMonth = currentYearMonth.minusMonths(1) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Prev",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Month
                        val monthName = currentYearMonth.month.getDisplayName(
                            java.time.format.TextStyle.SHORT, 
                            java.util.Locale.getDefault()
                        )
                        var monthDropdownExpanded by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { monthDropdownExpanded = true }
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(monthName, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = monthDropdownExpanded,
                                onDismissRequest = { monthDropdownExpanded = false },
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp),
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                java.time.Month.values().forEach { month ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                                            ) 
                                        },
                                        onClick = {
                                            currentYearMonth = java.time.YearMonth.of(currentYearMonth.year, month)
                                            monthDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        // Year
                        var yearDropdownExpanded by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { yearDropdownExpanded = true }
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(currentYearMonth.year.toString(), fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = yearDropdownExpanded,
                                onDismissRequest = { yearDropdownExpanded = false },
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp),
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                val baseYear = today.year
                                (baseYear - 5 .. 2099).forEach { year ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(year.toString()) },
                                        onClick = {
                                            currentYearMonth = java.time.YearMonth.of(year, currentYearMonth.month)
                                            yearDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    IconButton(
                        onClick = { currentYearMonth = currentYearMonth.plusMonths(1) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Days of week
                val daysOfWeek = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    daysOfWeek.forEach { day ->
                        Text(text = day, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Dynamic Grid Generation
                val daysInMonth = currentYearMonth.lengthOfMonth()
                val firstDayOfMonth = currentYearMonth.atDay(1)
                val startOffset = if (firstDayOfMonth.dayOfWeek.value == 7) 0 else firstDayOfMonth.dayOfWeek.value
                val prevMonth = currentYearMonth.minusMonths(1)
                val daysInPrevMonth = prevMonth.lengthOfMonth()
                
                val grid = mutableListOf<List<Pair<java.time.LocalDate, Boolean>>>()
                var currentDay = 1
                var nextMonthDay = 1
                
                for (row in 0..5) {
                    val week = mutableListOf<Pair<java.time.LocalDate, Boolean>>()
                    for (col in 0..6) {
                        val index = row * 7 + col
                        if (index < startOffset) {
                            val day = daysInPrevMonth - startOffset + index + 1
                            week.add(prevMonth.atDay(day) to false)
                        } else if (currentDay <= daysInMonth) {
                            week.add(currentYearMonth.atDay(currentDay) to true)
                            currentDay++
                        } else {
                            val nextMonth = currentYearMonth.plusMonths(1)
                            week.add(nextMonth.atDay(nextMonthDay) to false)
                            nextMonthDay++
                        }
                    }
                    grid.add(week)
                    if (currentDay > daysInMonth) break // Stop adding rows once the month is done
                }
                
                grid.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        row.forEach { (date, isCurrentMonth) ->
                            val isStreak = streakDates.contains(date)
                            val isToday = date == today
                            
                            val bgColor = when {
                                isStreak && isCurrentMonth -> Color(0xFF4A148C) // Purple
                                isToday && isCurrentMonth -> Color(0xFF212121) // Dark Gray
                                else -> Color.Transparent
                            }
                            
                            val textColor = when {
                                (isStreak || isToday) && isCurrentMonth -> Color.White
                                !isCurrentMonth -> Color.LightGray
                                else -> Color.Black
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = date.dayOfMonth.toString(), color = textColor, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileCard(
    title: String,
    titleColor: Color = Color(0xFF1E1E1E),
    icon: ImageVector,
    iconColor: Color,
    showArrow: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
