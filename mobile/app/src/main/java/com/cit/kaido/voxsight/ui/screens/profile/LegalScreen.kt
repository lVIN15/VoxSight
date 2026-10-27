package com.cit.kaido.voxsight.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LegalScreen(
    title: String,
    onBackClicked: () -> Unit
) {
    val legalContent = when {
        title.contains("Privacy", ignoreCase = true) -> PRIVACY_POLICY_TEXT
        else -> TERMS_OF_SERVICE_TEXT
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
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
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp)) // To balance the back button
        }

        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

        // --- Legal Content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = legalContent,
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 22.sp
            )
        }
    }
}

private val PRIVACY_POLICY_TEXT = """
Last updated: October 27, 2026

This Privacy Policy applies to the VoxSight app for mobile devices, together with any related services operated by Raveshack Team (collectively, the "Application"). Raveshack Team is hereby referred to as the "Service Provider".

1. Information Collection and Use

The Application collects information when you download and use it. This information may include information such as:
    
    - Your email
    - The pages of the Application that you visit 
    - The time and date of your visit 
    - The time spent on the Application
    - Your mobile operating system you use

2. Data Retention Policy

The Service Provider retains personal data based on its necessity for the stated purposes:

User Provided Data:
Retained for the duration of your use of the Application plus 12 months thereafter, unless longer retention is required by law

Automatically Collected Data:
Retained for up to 24 months from collection, unless longer retention is required for legal compliance

Aggregated and Anonymized Data: 
Retained indefinitely as it no longer identifies you

Data required for legal compliance: 
Retained as long as required by applicable law

3. Artificial Intelligence

The Application uses Artificial Intelligence (AI) technologies to enhance user experience and provide certain features. The AI components may process user data to deliver its neccesary service to the user. All AI processing is performed in accordance with this privacy policy and applicable laws. If you have questions about the AI features or data processing, please contact the Service Provider.

4. Your Rights

You may request access to, correction of, or deletion of your personal data held by the Service Provider. To exercise these rights, or to withdraw consent where processing is based on consent, contact the Service Provider at raveshackbusiness@gmail.com.

5. Opt-Out Rights

You can stop further collection of information from your mobile device by uninstalling the Application. Uninstalling will stop the Application from collecting data from your device, but it does not automatically delete information that has already been transmitted to the Service Provider or to third parties.
To request deletion of your personal data, to withdraw consent, or to exercise any of your rights, contact the Service Provider at raveshackbusiness@gmail.com.

6. Security

The Service Provider is concerned about safeguarding the confidentiality of your information. The Service Provider provides physical, electronic, and procedural safeguards to protect information the Service Provider processes and maintains.

7. Changes

The Service Provider may update this Privacy Policy from time to time. The Service Provider will notify you of material changes by posting the updated Privacy Policy with an effective date. Where required by law, the Service Provider will seek your consent to material changes before they take effect.

Previous versions of this Privacy Policy will be maintained and made available upon request by contacting the Service Provider at raveshackbusiness@gmail.com.

8. Your Consent 

Where processing is based on consent, you provide that consent by affirmatively opting in to the relevant feature or action. You may withdraw consent at any time without affecting processing carried out before withdrawal. Processing based on other lawful bases is carried out as described above.

Contact Us

If you have any questions regarding privacy while using the Application, or have questions about the practices, please contact the Service Provider via email at raveshackbusiness@gmail.com.

Please review this Privacy Policy carefully.
""".trimIndent()

private val TERMS_OF_SERVICE_TEXT = """
Last updated: October 27, 2026

These Terms of Service apply to the VoxSight app for mobile devices, together with any related services operated by Raveshack Team (collectively, the "Application"). Raveshack Team is hereby referred to as the "Service Provider".

1. Acceptance of Terms

Subject to your compliance with these Terms, the Service Provider grants you a limited, non-exclusive, non-transferable, revocable license to install and use the Application on a mobile device for personal or internal business purposes. You may not reproduce, distribute, modify, create derivative works from, reverse engineer, decompile, or disassemble the Application, except as and only to the extent that such activity is expressly permitted by applicable law.

2. Intellectual Property

The Service Provider retains all intellectual property rights in the Application, including its code, design, trademarks, service marks, trade names, logos, and branding (the "IP"). Nothing in these Terms grants you any license or right to use the Service Provider's trademarks, logos, or branding for any purpose. You agree not to remove, alter, or obscure any copyright, trademark, or other proprietary notices displayed in or on the Application.

3. Accepatable Use 

In importing music sheets through our app, you agree not to post content that:

- Is illegal or violates third-party intellectual property rights (copyright, trademark, patents)
- Is abusive, threatening, harassing, defamatory, or hate speech
- Contains discrimination or incitement to violence or illegal activity
- Is spam, phishing, or contains malware
- Violates the privacy or personal data rights of others
- Is misleading, false, or deceptive
- Contains explicit violence or sexual content

The Service Provider reserves the right to:

- Remove or disable access to content that violates these guidelines
- Suspend or terminate accounts of users who repeatedly violate these guidelines
- Cooperate with law enforcement if illegal content is reported
- Moderate, filter, or hide content that violates these Terms, applicable law, or the guidelines set out above

The Application stores and processes personal data that you have provided to the Service Provider in order to provide the Service. It is your responsibility to maintain the security of your mobile device and access to the Application.

The Service Provider strongly advises against jailbreaking or rooting your mobile device, which involves removing software restrictions and limitations imposed by the official operating system of your mobile device. Such actions could expose your mobile device to malware, viruses, malicious programs, compromise your mobile device's security features, and may result in the Application not functioning correctly or at all.

Third Party Services:

    - Google OAuth
    - Facebook

Please be aware that the Service Provider does not assume responsibility for certain aspects. Some functions of the Application require an active internet connection, which can be Wi-Fi or provided by your mobile network provider. The Service Provider cannot be held responsible if the Application does not function at full capacity due to lack of access to Wi-Fi or if you have exhausted your data allowance.

If you are using the application outside of a Wi-Fi area, please be aware that your mobile network provider's agreement terms still apply. Consequently, you may incur charges from your mobile provider for data usage during the connection to the application, or other third-party charges. By using the application, you accept responsibility for any such charges, including roaming data charges if you use the application outside of your home territory (i.e., region or country) without disabling data roaming. If you are not the bill payer for the device on which you are using the application, they assume that you have obtained permission from the bill payer.

Similarly, the Service Provider cannot always assume responsibility for your usage of the application. For instance, it is your responsibility to ensure that your device remains charged. If your device runs out of battery and you are unable to access the Service, the Service Provider cannot be held responsible.

Nothing in these Terms shall limit any rights you have under applicable consumer protection laws that cannot be lawfully excluded.

4. Entire Agreement

These Terms and Conditions, together with the Privacy Policy, constitute the entire agreement between you and the Service Provider concerning your use of the Application, superseding any prior agreements or understandings.

5. Changes to these Terms and Conditions

The Service Provider may periodically update their Terms and Conditions. Therefore, you are advised to review this page regularly for any changes. The Service Provider will notify you of any changes by posting the new Terms and Conditions on this page.

Previous versions of these Terms and Conditions will be maintained and made available upon request by contacting the Service Provider at raveshackbusiness@gmail.com.



Please review these Terms of Service carefully.
""".trimIndent()