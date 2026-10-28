# Mi entrenamiento

Aplicaciones Android y Wear OS para llevar un plan de gimnasio sin conexión. El teléfono importa un archivo XLSX con la estructura de `ejemplo.xlsx`; elegí plan, día y semana, y registrá las series en cualquiera de los dispositivos. La aplicación guarda sesiones e historial localmente. La sincronización utiliza la capa de datos de Google Play Services entre dispositivos emparejados con la misma cuenta.

## Compilar

Se requiere JDK 17, Android SDK 35 y Gradle 8.13. Ejecutá `./gradlew :app:assemblePhoneDebug :app:assembleWatchDebug`. Instalá el APK `phone` en el teléfono Android y el APK `watch` en un Galaxy Watch con Wear OS. Ambos deben usar el mismo ID de aplicación y estar emparejados.

## Estado y limitaciones

La interfaz adapta la sesión al teléfono y al reloj: acción principal visible, repeticiones y carga destacadas, descanso automático entre series y controles secundarios en Opciones. Las decisiones de diseño y la validación en dispositivos están en [training-ui.md](docs/training-ui.md).

El importador reconoce hojas visibles cuyo nombre contiene “plan”, días en la columna A, códigos en H, ejercicios en I y cinco semanas en grupos de cuatro columnas a partir de K. No evalúa fórmulas. Los enlaces de video se ingresan manualmente durante la sesión. El historial se guarda localmente. La sincronización usa la última versión completa recibida; editar simultáneamente en ambos dispositivos puede perder cambios. No se ha implementado Health Connect/Google Fit, ni importación de hipervínculos, edición persistente del plan, registro de carga real por serie, temporizador en segundo plano, exportación al servidor o acceso web. La sincronización y la interfaz del reloj requieren pruebas en dispositivos físicos.
