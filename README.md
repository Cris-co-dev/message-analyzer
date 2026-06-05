# message-analyzer

`message-analyzer` es un servicio web (receptor de webhooks + evaluador de alertas por palabras clave) construido con **Spring Boot 3.5.14** sobre **Java 17**, orquestado en local con **Docker Compose** junto a **n8n** para los flujos de respuesta, con pruebas end-to-end escritas en **Cypress** y un pipeline de **CI/CD** en GitHub Actions que publica la imagen Docker del API en **AWS ECS** (servicio Fargate).

**Estado:** API + tests Cypress ejecutados en GitHub Actions. La API dockerizada se despliega en **AWS ECS** (servicio `spring-alert-api-e642` en el clúster `default`). El flujo de n8n se ejecuta **únicamente en local** con Docker Compose; no forma parte del despliegue en AWS.

---

## 1. Arquitectura

La aplicación expone un único endpoint `POST /webhook` que recibe un mensaje y devuelve un veredicto booleano (`alert: true | false`) en función de las palabras clave configuradas en `src/main/resources/application.yaml` (categorías: *urgencia*, *error*, *ayuda*; 45 palabras clave en español). En local, n8n se conecta al API para enrutar el mensaje cuando la alerta es positiva.

```
   Cliente / Postman / Cypress
                  │
                  │  POST /webhook  { "user": "...", "message": "..." }
                  ▼
   ┌──────────────────────────────┐         ┌──────────────────────┐
   │  message-analyzer (API)      │ ─eval──▶│  n8n (workflows)     │
   │  Spring Boot 3.5  / Java 17  │         │  http://localhost:5678│
   │  http://localhost:8080       │         └──────────────────────┘
   └──────────────┬───────────────┘
                  │  docker build / docker push
                  ▼
           AWS ECS (Fargate)
           clúster: default
           servicio: spring-alert-api-e642
           imagen:  <ECR_REGISTRY>/<ECR_REPOSITORY>:<sha> + :latest
```

**Nota:** el modo "Express" de ECS (cuando se usa con un servicio Fargate pequeño) está implícito en la configuración existente del servicio (`spring-alert-api-e642`); el workflow descarga la *task definition* activa y solo sustituye la imagen, por lo que el pipeline no necesita declarar el *capacity provider* explícitamente.

---

## 2. Levantar el proyecto en local

### Prerrequisitos

| Herramienta | Obligatoria para | Notas |
|---|---|---|
| Docker + Docker Compose | Opción A (todo en contenedores) | Compose v2 (`docker compose ...`) |
| JDK 17 + Maven 3.9+ | Opción B (API sin Docker) | alternativa: usar el wrapper `./mvnw` |
| Node.js ≥ 18 (recomendado 20 LTS) | Tests Cypress | sólo si se van a ejecutar los E2E |

### Opción A — Todo con Docker Compose (recomendada)

Desde la raíz del repositorio:

```bash
docker compose up -d
```

Esto levanta dos contenedores en la red `app-network`:

| Servicio | Contenedor | Puerto local | URL |
|---|---|---|---|
| `api` | `alert-api` (construido desde `Dockerfile`) | `8080` | http://localhost:8080 |
| `n8n` | `n8n` (imagen `n8nio/n8n:latest`) | `5678` | http://localhost:5678 |

Verifica que el API está viva:

```bash
curl http://localhost:8080/health
# → "Correct"
```

Para detener y eliminar los contenedores:

```bash
docker compose down
```

### Opción B — API sin Docker (modo IDE)

Si prefieres ejecutar el API directamente desde tu IDE o sin contenedor, usa el wrapper de Maven incluido:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows (PowerShell o cmd)
mvnw.cmd spring-boot:run
```

La aplicación queda escuchando en `http://localhost:8080` (mismo puerto que con Docker).

> En esta opción **no se levanta n8n**; tendrías que arrancarlo por separado si lo necesitas (`docker run -d -p 5678:5678 -v n8n_data:/home/node/.n8n n8nio/n8n:latest`).

### Probar el endpoint `POST /webhook`

El contrato del request es un JSON con dos campos obligatorios no vacíos: `user` y `message`.

```bash
curl -i -X POST http://localhost:8080/webhook \
  -H "Content-Type: application/json" \
  -d '{"user":"Cristian","message":"Hola, esto es urgente resolver"}'
```

Respuestas esperadas:

* **200 OK** con `{"alert": true}` si el `message` contiene alguna palabra clave (case- y accent-insensitive, e.g. `urgente`, `Urgente`, `crítico`, `critico`, `atención inmediata`).
* **200 OK** con `{"alert": false}` si el `message` no contiene ninguna palabra clave.
* **400 Bad Request** con `{"error":"Bad Request","message":"...","timestamp":"..."}` si los campos están vacíos o el JSON está malformado.

### Cobertura y tests JUnit (opcional)

`./mvnw clean verify` (lo que ejecuta el pipeline de CI) corre los tests JUnit y aplica la **puerta de cobertura JaCoCo** (mínimo 70% LINE/BRANCH) sobre los paquetes `service`, `controller` y `dto.request`. El reporte queda en `target/site/jacoco/index.html` y el binario en `target/jacoco.exec`.

---

## 3. Ejecutar los tests Cypress

Los tests E2E validan el contrato HTTP del endpoint `/webhook`: rutas felices, validación 400, formato de respuesta, métodos HTTP no permitidos, idempotencia y casos de borde (Unicode, emojis, payloads muy largos).

> **Requisito previo:** el API debe estar corriendo en `http://localhost:8080`. Lánzalo primero con `docker compose up -d` (sección anterior).

### Instalación de dependencias (una sola vez)

```bash
npm install
# o, para una instalación reproducible a partir de package-lock.json:
npm ci
```

> Cypress `^15.16.0` es la única dependencia de desarrollo declarada en `package.json`; no hay scripts `cy:open` ni `cy:run` definidos, por lo que se invocan directamente con `npx`.

### Modo interactivo (Cypress UI)

```bash
npx cypress open
```

Esto abre la *Test Runner* de Cypress y permite seleccionar y depurar los specs en `cypress/e2e/`.

### Modo headless (estilo CI)

```bash
npx cypress run
```

Por defecto los resultados se imprimen en consola; las capturas de cada fallo caen en `cypress/screenshots/`.

### ¿Cuántos tests hay y dónde están?

* **Suite:** `cypress/e2e/alert-api.cy.js` (un único spec).
* **Total declarado:** 65 bloques `it(...)`, de los cuales uno es un bucle `forEach` que genera 45 casos en tiempo de ejecución (uno por palabra clave configurada). El número real de tests ejecutados es **109**.
* **Suites/contextos** (encabezados `context` dentro del spec): ruta feliz con alerta, ruta feliz sin alerta, *case-/accent-folding*, validación 400, JSON malformado 400, forma de la respuesta, semántica HTTP, idempotencia y casos de borde.

### Configuración de la URL base

`cypress.config.js` no define `baseUrl`; la URL `http://localhost:8080` está **hardcodeada** en los *custom commands* (`cypress/support/commands.js`). Si necesitas apuntar a otro entorno, edita directamente las URLs en `commands.js` o añade `baseUrl` en `cypress.config.js` y propágalo a los comandos.

### Custom commands disponibles

Definidos en `cypress/support/commands.js`:

* `cy.postWebhook(body, options?)` — envía `POST /webhook` con un cuerpo JSON; **no** aborta ante 4xx/5xx (`failOnStatusCode: false`).
* `cy.postRaw(rawBody, contentType?)` — envía `POST /webhook` con cuerpo crudo y `Content-Type` arbitrario (útil para tests de JSON malformado).
* `cy.assertAlertResponse(response, expected)` — afirma respuesta 200 con envolvente `{"alert": <expected>}`.
* `cy.assertErrorEnvelope(response, expectedError, messageMatcher?)` — afirma 400/500 con envolvente `{"error","message","timestamp"}` y que el timestamp sea una fecha válida.
* `cy.assertJsonContentType(response)` — afirma que la respuesta lleva `Content-Type: application/json`.

---

## 4. Importar y probar el workflow de n8n

Esta sección explica cómo levantar la instancia local de n8n (incluida en el `compose.yaml`), importar el workflow `User Message Forwarder` exportado al repositorio y verificar de punta a punta que el mensaje se enruta correctamente al API de análisis de alertas.

### 4.1 Ubicación del archivo

El workflow vive en:

```
n8n/workflows/User Message Forwarder.json
```

Es un JSON plano exportado desde n8n que describe los nodos, las conexiones y los parámetros del flujo. El directorio `n8n/` no contiene otros artefactos (no hay credenciales, ejemplos de payload ni archivos `.env`): todo lo necesario para ejecutarlo se define dentro del propio JSON.

### 4.2 Levantar n8n (recordatorio corto)

n8n se levanta con el mismo comando que el resto de la pila local (ver sección 2):

```bash
docker compose up -d
```

El servicio `n8n` está definido en `compose.yaml` con la imagen `n8nio/n8n:latest`, expone el puerto `5678` y queda accesible en:

```text
http://localhost:5678
```

Las variables de entorno relevantes (`N8N_HOST=localhost`, `N8N_PORT=5678`, `N8N_PROTOCOL=http`) ya están fijadas en el `compose.yaml`, por lo que no hace falta configurar nada extra para que la UI responda en `localhost`.

> **Aviso:** la definición actual de n8n **no monta un volumen** para `/home/node/.n8n`. Esto significa que credenciales, workflows y ejecuciones se **pierden** al hacer `docker compose down`. Ver §4.6.

### 4.3 Crear las credenciales necesarias

**El workflow no requiere credenciales.** Los tres nodos (`Webhook`, `Edit Fields` y `HTTP Request`) funcionan sin autenticación: el `Webhook` escucha HTTP plano y el `HTTP Request` apunta a la URL interna del API dentro de la red de Docker (`http://api:8080/webhook`), que tampoco exige credenciales. Puedes saltarte este paso.

> Detalle importante: el nodo `HTTP Request` usa el nombre de servicio de Docker `api` (no `localhost` ni `host.docker.internal`) porque el API y n8n comparten la red `app-network` definida en `compose.yaml`. Si por algún motivo se modifica esa URL, hay que asegurarse de que el destino sea alcanzable desde el contenedor de n8n.

### 4.4 Importar el workflow

1. Abre `http://localhost:5678` en el navegador. La primera vez que se accede a una instancia fresca de n8n se pide crear el **usuario propietario** (email + contraseña); guárdalos porque son los que usa el login por defecto.
2. En la barra lateral izquierda, haz clic en **Workflows**.
3. Pulsa el botón **+ Create workflow** (esquina superior derecha) para abrir un editor vacío.
4. En el menú de tres puntos (⋮) de la esquina superior derecha del editor, selecciona **Import from File...**.
5. Selecciona el archivo `n8n/workflows/User Message Forwarder.json` del repositorio.
6. n8n no preguntará por credenciales (ningún nodo las referencia).
7. **Activa el workflow** con el toggle **Active** en la esquina superior derecha del editor (por defecto el JSON exportado trae `active: false`).

> El JSON exportado tiene `pinData: {}` (vacío), así que n8n no trae datos de prueba pre-cargados. Para probar el workflow tendrás que disparar el webhook manualmente (siguiente paso).

### 4.5 Probar el workflow end-to-end

El workflow es un *forwarder*: recibe un `POST` en su webhook, extrae `user` y `message`, y los reenvía al endpoint del API. Como el webhook de n8n es el punto de entrada del flujo, la prueba correcta es **llamar directamente al webhook de n8n** y verificar que el API recibe la llamada y devuelve `alert: true|false`.

#### Paso 1 — Disparar el webhook de n8n

El nodo `Webhook` está configurado con el path `f91c7409-e77f-4471-90fc-65079d333687`, por lo que la URL completa del trigger es:

```text
http://localhost:5678/webhook/f91c7409-e77f-4471-90fc-65079d333687
```

##### Caso positivo (debe devolver `alert: true`)

El mensaje contiene palabras clave reales del `application.yaml` del API (`Urgente` y `Ayuda`):

```bash
# Linux / macOS
curl -i -X POST http://localhost:5678/webhook/f91c7409-e77f-4471-90fc-65079d333687 \
  -H "Content-Type: application/json" \
  -d '{"user":"Cristian","message":"URGENTE: el sistema de pagos está caído, necesitamos AYUDA inmediata"}'

# Windows (PowerShell o cmd)
curl.exe -i -X POST http://localhost:5678/webhook/f91c7409-e77f-4471-90fc-65079d333687 ^
  -H "Content-Type: application/json" ^
  -d "{\"user\":\"Cristian\",\"message\":\"URGENTE: el sistema de pagos está caído, necesitamos AYUDA inmediata\"}"
```

Respuesta esperada (devuelta por el nodo `Webhook` con `responseMode: lastNode`, que reenvía la respuesta del `HTTP Request`):

```json
{
  "alert": true
}
```

##### Caso negativo (debe devolver `alert: false`)

Mensaje neutro, sin palabras clave:

```bash
# Linux / macOS
curl -i -X POST http://localhost:5678/webhook/f91c7409-e77f-4471-90fc-65079d333687 \
  -H "Content-Type: application/json" \
  -d '{"user":"Cristian","message":"Hola, ¿cómo están hoy?"}'

# Windows (PowerShell o cmd)
curl.exe -i -X POST http://localhost:5678/webhook/f91c7409-e77f-4471-90fc-65079d333687 ^
  -H "Content-Type: application/json" ^
  -d "{\"user\":\"Cristian\",\"message\":\"Hola, ¿cómo están hoy?\"}"
```

Respuesta esperada:

```json
{
  "alert": false
}
```

#### Paso 2 — Verificar la ejecución en la UI de n8n

1. En la barra lateral izquierda, abre **Executions**.
2. La ejecución más reciente aparecerá arriba; haz clic en ella.
3. La vista muestra los **tres nodos en orden** (`Webhook` → `Edit Fields` → `HTTP Request`) con sus entradas y salidas:
   * `Webhook`: el JSON crudo que enviaste por `curl` (queda accesible bajo `body`).
   * `Edit Fields`: los dos campos extraídos (`user`, `message`).
   * `HTTP Request`: la respuesta del API (`{ "alert": true }` o `{ "alert": false }`).
4. Confirma que el `status` de la ejecución es **Success** y que el JSON de salida del último nodo coincide con la respuesta esperada del caso correspondiente.

### 4.6 Solución de problemas

* **El API no recibe la llamada (`Connection refused` a `api:8080`).** El nodo `HTTP Request` apunta a `http://api:8080/webhook`, que sólo es resolvable desde dentro de la red `app-network` de Docker. Si el API se arrancó *fuera* de Docker (Opción B de la sección 2, por ejemplo con `./mvnw spring-boot:run`), el hostname `api` no existe y la llamada falla. Soluciones: arrancar también el API con `docker compose up -d api`, o cambiar la URL del nodo a `http://host.docker.internal:8080/webhook` (en Linux habría que añadir `extra_hosts: ["host.docker.internal:host-gateway"]` al servicio `n8n` en `compose.yaml`).
* **El webhook devuelve 404.** El path del webhook (`f91c7409-e77f-4471-90fc-65079d333687`) está hardcodeado en el JSON exportado. Si re-importas el workflow después de haberlo modificado o regenerado los IDs, hay que usar el path nuevo que aparezca en el nodo `Webhook`. También: el workflow debe estar **activo** (toggle "Active" en la esquina superior derecha) — un workflow inactivo no responde al webhook.
* **Los nodos aparecen con un signo de pregunta "?" en rojo.** Indica que la versión de n8n que importa el JSON no reconoce algún tipo de nodo (`n8n-nodes-base.webhook` v2.1, `n8n-nodes-base.set` v3.4 o `n8n-nodes-base.httpRequest` v4.4). La imagen `n8nio/n8n:latest` debería traerlos todos; si no, fija una versión más nueva en `compose.yaml` (por ejemplo `n8nio/n8n:1.95` o superior).
* **Las credenciales y los workflows desaparecen tras `docker compose down && docker compose up -d`.** El servicio `n8n` en `compose.yaml` **no monta un volumen** para `/home/node/.n8n`. Para persistir, añade al servicio:
  ```yaml
  volumes:
    - n8n_data:/home/node/.n8n
  ```
  y declara el volumen en el bloque `volumes:` del `compose.yaml`.
* **El API responde 400 a la llamada del workflow.** El nodo `Edit Fields` extrae `user` y `message` del cuerpo; si el `curl` original no envía esos campos, o envía `user`/`message` vacíos, el API rechaza con `400 Bad Request`. Revisa el JSON de entrada en la pestaña **Executions** y comprueba que ambos campos son strings no vacíos.

---

## 5. CI/CD — Despliegue a AWS

El proyecto tiene **un único workflow** de GitHub Actions: `.github/workflows/deploy.yml` (nombre: `CI-CD`). Combina la fase de tests (CI) y la fase de despliegue (CD) en un mismo pipeline con dos *jobs* encadenados.

### Disparador

```yaml
on:
  push:
    branches: [main]
```

El pipeline se ejecuta **sólo en `push` a `main`**. Los *pull requests* no disparan el workflow.

### Etapas del pipeline

| # | Job | Pasos clave |
|---|---|---|
| 1 | `test` | `actions/checkout@v4` → `actions/setup-java@v4` (Temurin 17, caché de Maven) → `mvn clean verify` (corre JUnit y aplica la puerta JaCoCo 70%) |
| 2 | `deploy` *(needs: test)* | Configura credenciales AWS → login en ECR → `docker build` + `docker push` → descarga la *task definition* activa → renderiza con la nueva imagen → despliega en ECS |

Detalles del *job* `deploy`:

* **Autenticación AWS:** `aws-actions/configure-aws-credentials@v4` con `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY`; región leída del secret `AWS_REGION`.
* **Push a ECR:** `aws-actions/amazon-ecr-login@v2` resuelve el registro. La imagen se etiqueta con dos *tags*:

  ```text
  <ECR_REGISTRY>/<ECR_REPOSITORY>:<github.sha>
  <ECR_REGISTRY>/<ECR_REPOSITORY>:latest
  ```

  y se hace `docker push` de ambos.
* **Actualización de la *task definition*:** se descarga la definición actual del servicio (`aws ecs describe-task-definition --task-definition default-spring-alert-api-e642`), `aws-actions/amazon-ecs-render-task-definition@v1` sustituye el campo `image` del contenedor `Main` por la nueva imagen, y `aws-actions/amazon-ecs-deploy-task-definition@v2` registra y aplica la nueva revisión.
* **Despliegue en ECS:** servicio `spring-alert-api-e642`, clúster `default` (Fargate). El *capacity provider* y la configuración de red (subnets, security groups, IP pública) están implícitos en la *task definition* que el workflow descarga; el pipeline **no** los redefine.

### GitHub Secrets requeridos

| Secret | Obligatorio | Para qué se usa |
|---|---|---|
| `AWS_ACCESS_KEY_ID` | sí | Credenciales AWS de largo plazo (alternativa: OIDC + `AWS_ROLE_TO_ASSUME`, no configurado aquí) |
| `AWS_SECRET_ACCESS_KEY` | sí | Pareja del anterior |
| `AWS_REGION` | sí | Región de ECR y ECS (la región concreta la defines tú en el secret) |
| `ECR_REPOSITORY` | sí | Nombre del repositorio ECR donde se publica la imagen |

> Los nombres del clúster (`default`) y del servicio (`spring-alert-api-e642`) están **hardcodeados** en el workflow, no en secrets.

### Lo que **no** se despliega

* **n8n no se despliega a AWS.** El contenedor `n8n` definido en `compose.yaml` es estrictamente local. Los workflows exportados viven en `n8n/workflows/User Message Forwarder.json` y deben importarse manualmente en la instancia local de n8n.
* Los tests Cypress **no** se ejecutan dentro de este workflow (la *job* `test` corre sólo JUnit + JaCoCo vía Maven). Los E2E se corren en local; integrarlos al pipeline es un trabajo futuro.

---

## 6. Estructura del proyecto

```
.
├── src/                   # Código fuente Spring Boot (main + test)
│   ├── main/java/com/cdortiz/message_analyzer/
│   │   ├── controller/    # WebhookController.java  (POST /webhook, GET /health)
│   │   ├── service/       # AlertEvaluatorService.java
│   │   ├── dto/           # WebhookRequest, AlertResponse, ErrorResponse
│   │   ├── config/        # AlertProperties (binding de application.yaml)
│   │   └── exception/     # GlobalExceptionHandler (400, 500)
│   ├── main/resources/    # application.yaml (45 palabras clave)
│   └── test/              # Tests JUnit (controller, service, dto, validación)
├── cypress/
│   ├── e2e/               # alert-api.cy.js (109 tests E2E)
│   ├── fixtures/          # example.json
│   ├── support/           # commands.js, e2e.js
│   └── screenshots/       # capturas de fallos
├── n8n/
│   └── workflows/         # User Message Forwarder.json
├── .github/
│   └── workflows/         # deploy.yml  (CI + CD)
├── compose.yaml           # Orquestación local (api + n8n)
├── Dockerfile             # Imagen API (multi-stage: temurin-17 + JRE 17)
├── pom.xml                # Spring Boot 3.5.14, Java 17, JaCoCo 0.8.11
├── package.json           # Cypress ^15.16.0
├── mvnw, mvnw.cmd         # Maven wrapper (Linux/macOS, Windows)
└── LICENSE, LICENSE.txt   # MIT
```

---

## 7. Endpoints del API

| Método | Path | Descripción | Respuesta exitosa |
|---|---|---|---|
| `POST` | `/webhook` | Evalúa un mensaje contra las palabras clave configuradas | `200` con `{"alert": true|false}` |
| `GET`  | `/health` | Liveness probe (sin dependencias externas) | `200` con cuerpo `"Correct"` |

Códigos de error del endpoint `POST /webhook`:

| Código | Cuándo | Cuerpo |
|---|---|---|
| `400` | `user` o `message` vacíos / ausentes | `{"error":"Bad Request","message":"...","timestamp":"..."}` |
| `400` | JSON malformado, body vacío, body que no es objeto | `{"error":"Bad Request","message":"Malformed JSON request","timestamp":"..."}` |
| `405` | Métodos distintos a `POST` | (respuesta estándar de Spring) |

---

## 8. Licencia

`MIT`. Ver [`LICENSE`](LICENSE) / [`LICENSE.txt`](LICENSE.txt) — Copyright (c) 2026 Cristian David.
