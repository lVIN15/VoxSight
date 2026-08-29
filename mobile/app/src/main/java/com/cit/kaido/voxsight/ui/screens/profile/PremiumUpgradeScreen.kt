package com.cit.kaido.voxsight.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Music

@Composable
fun PremiumUpgradeScreen(
    onCancelClicked: () -> Unit
) {
    val topPurple = Color(0xFF38036B)
    val yellowGold = Color(0xFFFACC15)
    val bgLight = Color(0xFFF4F4FA)
    val textGray = Color(0xFF6B7280)
    val textPurple = Color(0xFF6B21A8)
    val iconBgPurple = Color(0xFFF3E8FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgLight)
    ) {
        // Top Section (Purple)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    color = topPurple,
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
        ) {
            // Background decorative notes
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 20.dp, y = 40.dp)
            )
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-20).dp, y = (-20).dp)
            )
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-60).dp, y = 80.dp)
            )

            // Content
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Crown icon in circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Crown,
                        contentDescription = "Premium Crown",
                        tint = yellowGold,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "UPGRADE TO",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "PREMIUM",
                    color = yellowGold,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Subtitle Text
        Text(
            text = "With VoxSight Premium, you can elevate your practice sessions to the next level:",
            color = textGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                FeatureRow(
                    icon = Lucide.Cloud,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("Upload an ")
                        withStyle(style = SpanStyle(color = textPurple, fontWeight = FontWeight.Medium)) {
                            append("increased amount")
                        }
                        append(" of music sheets for you to practice.")
                    }
                )

                FeatureRow(
                    icon = Lucide.Cloud,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("Upgraded cloud storage capacity for your converted sheets.")
                    }
                )

                FeatureRow(
                    icon = Lucide.Download,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("Ability for you to ")
                        withStyle(style = SpanStyle(color = textPurple, fontWeight = FontWeight.Medium)) {
                            append("download")
                        }
                        append(" your converted sheets outside of this app.")
                    }
                )

                FeatureRow(
                    icon = Lucide.Plus,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("And many more exclusive features...")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Price Button
        Button(
            onClick = { /* TODO: Implement purchase */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = topPurple),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "₱ 299.00 / month",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cancel Button
        TextButton(
            onClick = onCancelClicked,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Cancel",
                color = Color(0xFFF44336), // Red
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    text: androidx.compose.ui.text.AnnotatedString
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = Color(0xFF4B5563),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
