# Llave Mambisa

Aplicación Android de código abierto para intercambiar mensajes cifrados de extremo a extremo. Usa Google Tink para primitivas criptográficas y Jetpack Compose para la interfaz de usuario. Los mensajes pueden enviarse por SMS o compartirse a través de cualquier otra plataforma (WhatsApp, Telegram, email, etc.).

## Características

- **Cifrado E2E**: Los mensajes se cifran con AES-256-GCM usando un ratchet de claves efímeras (forward secrecy). Las claves de identidad se intercambian por QR.
- **Firma digital**: Cada mensaje va firmado con Ed25519 para garantizar autenticidad del remitente y prevenir suplantación.
- **Sesiones persistentes**: El estado del ratchet se persiste en Room cifrado campo por campo, permitiendo retomar conversaciones sin pérdida de sincronía.
- **Compartir cifrado**: Cifra un mensaje y compártelo por cualquier plataforma (WhatsApp, Telegram, Signal, email...) mediante la hoja de compartir de Android.
- **Pegar cifrado**: Pega un texto cifrado desde el portapapeles para descifrarlo directamente en la conversación.
- **Envío por SMS**: El método tradicional: cifra y envía directamente como SMS.
- **Inbox polling**: En Android 12+ donde `SMS_RECEIVED` está restringido para apps no predeterminadas, la app escanea periódicamente la bandeja de entrada en busca de mensajes cifrados no procesados.
- **Intercambio de claves por QR**: Escanea o muestra un código QR con las claves públicas para establecer contacto de forma segura.
- **Contactos del dispositivo**: Integración con la libreta de contactos del sistema.
- **Tema oscuro/claro adaptable**: Interfaz Material 3 con diseño inspirado en WhatsApp.

## Arquitectura

```
MVVM + DI manual + Room + Jetpack Compose
```

```
app/
├── crypto/           # Cifrado (KeyManager, CryptoManager, EphemeralKeyManager, SmsWireFormat)
├── data/
│   ├── local/        # Room (AppDatabase, SecureFieldCrypto, entidades, daos)
│   └── repository/   # SmsRepository, DeviceContactsRepository, SmsInboxReader
├── di/               # AppContainer (inyección de dependencias manual)
├── domain/model/     # Modelos de dominio (Contact, ChatMessage, etc.)
├── sms/              # SmsReceiver, SmsStatusReceiver
├── ui/
│   ├── camera/       # Escáner QR
│   ├── navigation/   # AppNavigation
│   ├── screens/      # Composes (ChatScreen, ConversationsScreen, ProfileScreen, etc.)
│   ├── theme/        # Tema Material 3
│   └── viewmodel/    # ViewModels
└── util/             # Logger, ErrorHandler, InputValidator, QrPayload
```

### Flujo criptográfico

1. **Identidad**: `KeyManager` gestiona un par Ed25519 (firma) y un par HPKE/X25519 (intercambio híbrido), generados por Tink y cifrados en reposo con el Android Keystore.
2. **Bootstrap**: El primer mensaje envía un seed aleatorio cifrado con HPKE (usa la clave híbrida pública del destinatario). De este seed se deriva la root key del ratchet.
3. **Ratchet**: `EphemeralKeyManager` implementa un ratchet simétrico: por cada mensaje se avanza la chain key, se deriva una clave de mensaje, y se cifra con AES-256-GCM.
4. **Autenticación**: Cada envelope se firma con Ed25519; el receptor verifica la firma antes de tocar el ciphertext (fail-fast contra falsificación).
5. **Wire format**: JSON compacto con prefijo `ESM1:`, campos con nombres de una letra para minimizar tamaño.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM, DI manual (AppContainer) |
| Criptografía | Google Tink 1.16.0 (AEAD, HybridEncrypt, PublicKeySign/Verify) |
| Persistencia | Room 2.7.1 + SecureFieldCrypto (AES-256-GCM campo por campo) |
| SMS | SmsManager, SmsReceiver, content://sms/inbox |
| QR | ML Kit Barcode Scanning + ZXing |
| Cámara | CameraX 1.4.1 |
| Biometría | AndroidX Biometric |
| Mínimo SDK | 26 (Android 8.0) |

## Requisitos

- Android 8.0+ (API 26)
- Permisos: `RECEIVE_SMS`, `SEND_SMS`, `READ_SMS`, `CAMERA`, `READ_CONTACTS`

## Compilación

```bash
./gradlew assembleDebug
```

El APK firmado con debug se genera en `app/build/outputs/apk/debug/`.

## Licencia

**GNU General Public License v3.0** — ver [LICENSE](LICENSE).

Este programa es software libre: puede redistribuirlo y/o modificarlo bajo los términos de la GNU General Public License publicada por la Free Software Foundation, versión 3 o (a su elección) cualquier versión posterior.

Este programa se distribuye SIN GARANTÍA ALGUNA; sin siquiera la garantía implícita de COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR. Ver la [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html) para más detalles.
