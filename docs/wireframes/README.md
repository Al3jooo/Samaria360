# Wireframes de Samaria360

**Versión 2 — 12/10/2026.** Reemplaza a la versión del Documento Entregable 2; la versión anterior queda en el historial de Git. Cada imagen tiene aquí su descripción escrita, que es la referencia cuando un detalle de la imagen no se lea bien.

**Imágenes de los wireframes propuestos por arquitectura (13/11/2026, DEC-268).** WF-01 a WF-10 los dibujó el equipo de diseño. Los nuevos (WF-12 `_004` a `_006`, WF-13 a WF-26, WF-06 para atractivos y `WF-05-06-11-cambios-turista`) tienen un `.png` con el mismo nombre que su `.puml`, dibujado por arquitectura en el mismo estilo de boceto (escala de grises, sin identidad visual) a partir del `.puml` y de la pantalla implementada. Un `.png` con varias láminas muestra la pantalla, sus estados y sus diálogos; las notas en recuadro punteado son anotaciones, no elementos de la pantalla. Siguen pendientes de validación del equipo.

**Reglas:**
- **Trazabilidad:** toda pantalla del prototipo corresponde a un wireframe de esta carpeta. Si un elemento queda fuera del alcance de la iteración, se muestra deshabilitado («Próximamente») y se lista como pendiente en `docs/trazabilidad.md`.
- **Guía, no plano:** los wireframes fijan la estructura, el contenido y el orden de los elementos. El diseño visual es libre dentro de la identidad «Santa Marta» (`frontend/src/app/estilos.css`).
- **Fuente de requisitos:** si un wireframe contradice el SRS (`docs/srs/especificacion de requisitos.pdf`), manda el SRS y la diferencia se anota en la trazabilidad. Ejemplo: los wireframes dicen «aforo» donde el SRS dice «afluencia» (RF39).

## Elementos comunes

**Pantallas de la plataforma (WF-04 a WF-10):**
- **Encabezado:** logo «Samaria360»; navegación **Búsqueda, Servicios, Asistente IA**; selector **ES / EN** (RF03, RNF08); con sesión, avatar circular con las iniciales (p. ej. «MG»). Sin sesión, el prototipo muestra además el enlace «Inicio de sesión» (los wireframes solo dibujan el estado con sesión).
- **Banner de avisos del municipio (RF40, DEC-67):** debajo del encabezado, en todas las pantallas con el encabezado de la plataforma. Muestra todos los avisos activos, del más reciente al más antiguo, con su tipo. Se cierra y no vuelve en la sesión del navegador; sin avisos activos, no aparece. WF-04 lo dibuja solo en el inicio, pero RF40 lo pide «en la parte superior de la interfaz para todos los usuarios»: por eso es común («Mejora de diseño»). Estructura en WF-14.
- **Pie de página:** logo; columnas **Explorar** (Alojamiento, Gastronomía, Tours, Alquiler de equipos), **Municipio** (Noticias, Agenda de eventos), **Prestadores** (Registrar servicio, Panel del gestor), **Legal y Soporte** (Términos y condiciones, Políticas de privacidad, Preguntas frecuentes — RF46, RF26). Texto inferior: «© 2026 Alcaldía de Santa Marta · Samaria360». «Términos y condiciones» lleva a WF-15, «Políticas de privacidad» a su sección 4 y «Preguntas frecuentes» a WF-13. «Agenda de eventos» (RF25) lleva a WF-26: no está en la v2 y se agregó en la iteración E, sesión B2 («Mejora de diseño», DEC-246).

**Pantallas de acceso (WF-01 a WF-03):**
- **Encabezado reducido:** solo logo y selector ES / EN.
- **Pie reducido:** «© 2026 Samaria360 · Plataforma turística verificada», Términos y condiciones, Políticas de privacidad, Ayuda / Soporte.

## Índice

| Archivo | Pantalla | Caso de uso | Requisitos | Alcance del prototipo (Construcción) |
|---|---|---|---|---|
| WF-01-inicio-sesion.png | Inicio de sesión | Iniciar sesión | RF15, RF03, RF34, RF46 | **Sí** (sin recuperación de contraseña) |
| WF-02-registro-turista.png | Registro de turista | Registrarse | RF01 | **Sí** (`/registro`) |
| WF-03-registro-prestador.png | Registro de prestador | Registrarse | RF02 | **Sí** (`/registro`, sin razón social en el formulario) |
| WF-04-inicio-busqueda.png | Inicio y búsqueda | Buscar oferta turística | RF16, RF40, RF04 | **Sí** (el banner de avisos es un elemento común, WF-14; «Destacados» con criterio provisional) |
| WF-05-exploracion.png | Exploración: mapa y resultados | Buscar oferta, filtrar, ordenar, navegar el mapa | RF16, RF10, RF23, RF27, RF38, RF39, RF50, RF61 | **Sí** (núcleo del catálogo, incluye «Más filtros») |
| WF-06-detalle.png | Detalle de lugar o servicio | Consultar detalle de oferta | RF37, RF39, RF51, RF54, RF57, RF58 | Sí (versión mínima: solo servicios y los datos que existen). La ficha de un atractivo tiene su propia variante (`WF-06-detalle-atractivo.puml`, iteración E) |
| WF-07-asistente-ia.png | Asistente virtual IA | Interactuar con el asistente | RF04, RF35, RF36, RF59 | No |
| WF-08-registrar-servicio.png (+ `WF-08-registrar-servicio.puml`, ampliación) | Registrar servicio comercial | Registrar servicio comercial | RF09, RF10, RF27, RF37, RF48, RF58 | Backend en la iteración C (sesión A1); **pantalla hecha en B1** (`/prestador/servicios/nuevo`), sin fotos (RF48, descartado en DEC-54), «cobra por» ni impuestos. La ampliación en Salt está **propuesta por arquitectura, pendiente de validación del equipo** |
| WF-09-registrar-atractivo.png (+ `WF-09-registrar-atractivo.puml`, ampliación) | Registrar atractivo turístico | Registrar atractivo; Actualizar atractivo | RF06, RF07, RF24, RF27, RF33, RF58 | **Sí** (iteración E, sesión B1; `/gestor/atractivos/nuevo` y, en edición, `/gestor/atractivos/:id`), con la casilla de veracidad deshabilitada y sin editar las fotos de un atractivo registrado. La ampliación en Salt está **propuesta por arquitectura, pendiente de validación del equipo** |
| WF-10-disponibilidad-reserva.png | Disponibilidad y reserva **(nuevo)** | Reservar servicio | RF11, RF20, RF37, RF44 | **Sí** (sin RNT, impuestos, pagos ni «Notificarme si se libera») |
| — (WF-11, sin imagen) | Mis reservas **(pendiente de diseño)** | Reservar servicio | RF12, RF18, RF44, RF52 | Sí (diseño provisional) |
| WF-12-administrar-cuentas.puml (+ `.png` y `.svg`) | Administrar cuentas **(nuevo)** | Administrar cuentas; Cambiar rol de cuenta | RF28, RF22, RF62, RF45 | **Sí** (sin la reactivación del prestador; RF45 implementado en B2). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-13-preguntas-frecuentes.puml (+ `.png`) | Preguntas frecuentes **(nuevo)** | Gestionar FAQs | RF26, RF47 | **Sí** (sesión B). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-14-avisos.puml (+ `.png`) | Avisos generales: banner y gestión **(nuevo)** | Crear y publicar aviso general | RF40 | **Sí** (sesión B). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-15-terminos.puml (+ `.png`) | Términos y condiciones **(nuevo)** | Consultar términos y condiciones | RF46 | **Sí** (sesión B). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-16-perfil-turista.puml (+ `.png`) | Perfil del turista **(nuevo)** | Actualizar datos personales, Actualizar intereses turísticos | RF53 | **Sí** (sesión B de B1). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-17-mis-favoritos.puml (+ `.png`) | Mis favoritos **(nuevo)** | Guardar favoritos | RF54 | **Sí** (sesión B de B1; el «Ver detalle» de un atractivo, desde la iteración E). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-18-mis-servicios.puml (+ `.png`) | Mis servicios **(nuevo)** | Registrar servicio comercial (flujo «Consultar mis servicios», DEC-129) | RF09 | Backend en la iteración C (A1); **pantalla hecha en B1** (`/prestador/servicios` y `/prestador/servicios/:id`). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-19-aprobar-servicios.puml (+ `.png`) | Aprobar servicios **(nuevo)** | Aprobar o rechazar servicio; Consultar listado pendiente; Justificar rechazo de servicio | RF21 | Backend en la iteración C (A1); pantalla hecha en B2 (DEC-174 a DEC-181). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-20-actualizar-precio.puml (+ `.png`) | Actualizar precio **(nuevo)** | Actualizar tarifas | RF32 | **Sí** (iteración D, sesión B; `/prestador/servicios/:id/precio`). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-21-datos-comerciales-servicio.puml (+ `.png`) | Editar datos comerciales del servicio **(nuevo)** | Actualizar datos comerciales (flujo B) | RF43 | **Sí** (iteración D, sesión B; `/prestador/servicios/:id/datos-comerciales`). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-22-perfil-comercial.puml (+ `.png`) | Mi perfil comercial **(nuevo)** | Actualizar datos comerciales (flujo A) | RF43 | **Sí** (iteración D, sesión B; `/prestador/perfil`). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-23-historial-reservas-prestador.puml (+ `.png`) | Historial de reservas del Prestador **(nuevo)** | Consultar historial | RF12 | **Sí** (iteración D, sesión B; `/prestador/reservas`). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-24-atractivos-gestor.puml (+ `.png`) | Atractivos, panel del gestor **(nuevo)** | Actualizar atractivo (flujo «Consultar atractivos»); Cambiar estado operativo | RF07, RF33 | **Sí** (iteración E, sesión B1; `/gestor/atractivos`), con los filtros deshabilitados. **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-25-eventos-gestor.puml (+ `.png`) | Eventos, panel del gestor, y Registrar evento **(nuevo)** | Registrar evento en agenda (con el flujo «Consultar eventos registrados») | RF25 | **Sí** (iteración E, sesión B2; `/gestor/eventos` y `/gestor/eventos/nuevo`). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-26-agenda-eventos.puml (+ `.png`) | Agenda de eventos **(nuevo)** | Consultar agenda de eventos | RF25 | **Sí** (iteración E, sesión B2; `/agenda`, pública). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-06-detalle-atractivo.puml (+ `.png`) | Ficha pública de un atractivo **(nuevo)** | Consultar detalle de atractivo | RF06, RF07, RF24, RF27, RF33, RF58 | **Sí** (iteración E, sesión B1; `/atractivos/:id`). **Propuesto por arquitectura, pendiente de validación del equipo** |
| WF-05-06-11-cambios-turista.puml (+ `.png`) | Cambios en WF-05, WF-06 y WF-11 **(nuevo)** | Consultar historial de búsquedas, Guardar favoritos, Compartir reserva completada | RF60, RF54, RF49 | **Sí** (sesión B de B1). **Propuesto por arquitectura, pendiente de validación del equipo** |

## WF-01 — Inicio de sesión

- Subtítulo «Plataforma de turismo de Santa Marta».
- Pestañas **Iniciar sesión** (activa) / **Registrarse**.
- Texto: «Accede con el correo con el que te registraste o crea una cuenta nueva.»
- Tarjeta «Ingresa con tu cuenta»: **Correo electrónico** (icono de sobre, `nombre@correo.com`), **Contraseña** (icono de candado), enlace **¿Olvidaste tu contraseña?** (RF34), aviso **«3 intentos fallidos bloquearán la cuenta por 15 minutos.»** (RF15), botón **Iniciar sesión** con icono, texto legal sobre Términos, Condiciones y Políticas de Privacidad.
- Cambio respecto a la v1: ya no tiene el bloque «Plataforma» ni el título «Samaria360» grande; encabezado y pie reducidos.

## WF-02 — Registro de turista

Pestaña **Registrarse** activa; «Selecciona el tipo de cuenta que quieres registrar.»; opción **Turista** seleccionada. «Formulario – Turista»: primer nombre, segundo nombre (opcional), primer apellido, segundo apellido (opcional), correo electrónico, crear contraseña (mínimo 8 caracteres), país de origen (lista), categorías de interés (Naturaleza, Cultura, Aventura), casilla de Política de Privacidad y botón **Crear cuenta de turista**.

## WF-03 — Registro de prestador

Pestaña **Registrarse** activa; opción **Prestador de Servicios** seleccionada. «Formulario – Prestador de Servicios»: nombre del representante legal, número RNT, correo corporativo, teléfono, contraseña; aviso **«Su cuenta quedará Pendiente de Validación.»** (RF02); casilla de Política de Privacidad (nueva en la v2); botón **Registrar prestador**.

**Prototipo:** la razón social no está en este formulario, y la API la acepta como opcional (`razonSocial`; decisión D2, migración V5, `docs/trazabilidad.md`). El correo corporativo es también el correo de inicio de sesión.

## WF-04 — Inicio y búsqueda

- **Banner de aviso** del municipio, cerrable (RF40). Ej.: cierre del sendero Cascada El Salto por lluvias.
- Titular «Encuentra alojamiento, gastronomía y tours en Santa Marta» y texto de apoyo.
- **Barra de búsqueda** con botón **Buscar**.
- «Explora por categoría»: **Alojamiento, Gastronomía, Tours, Alquiler de equipos**, con icono.
- «Destacados esta semana»: 3 tarjetas con imagen, nombre, categoría, calificación, precio «desde», disponibilidad y **Ver detalle**. Cambio respecto a la v1: sin el enlace «Reservar».

**Prototipo:** la búsqueda y las categorías llevan a WF-05 con el filtro aplicado. El banner de avisos es un elemento común de la plataforma (ver «Elementos comunes» y WF-14). Su fuente de datos es `GET /api/v1/catalogo/avisos`; lo implementó la sesión B de «Contenido público» (DEC-72).

**Nota:** El botón flotante del asistente se retiró por decisión del equipo; el acceso a RF04 queda en la navegación.

## WF-05 — Exploración: mapa y resultados

- Migas de pan: Samaria360 › Exploración interactiva.
- Título «Exploración de Atractivos y Servicios en Santa Marta» y texto «Monitorea la afluencia en tiempo real, filtra por cercanía y planifica tus recorridos…».
- **Barra de filtros** «Filtros de búsqueda y afluencia»: texto («Filtrar por nombre, vereda o actividad»), **rango de precio**, **distancia máxima (km)**, casilla **acceso para personas con movilidad reducida**, botón **Aplicar filtros**.
- **Más filtros (adición del equipo, no dibujada en la imagen):** botón o panel desplegable que muestra filtros propios de la categoría elegida, a partir de los campos de RF10:
  - Alojamiento: servicios incluidos (WiFi, desayuno, piscina…), capacidad de huéspedes, tipo de habitación.
  - Gastronomía: opción vegetariana, tipo de cocina.
  - Tours: nivel de dificultad, duración máxima.
  - Alquiler de equipos: tipo de vehículo o equipo.
  - Sin categoría elegida, «Más filtros» invita a elegir una. Los atractivos no tienen campos por categoría, así que estos filtros solo aplican a servicios.
- **Mapa** con marcadores; leyenda **Bajo (< 40 %)**, **Medio (40–75 %)**, **Alto (> 75 %)**; **Centrar en mi ubicación**.
- «Resultados en Santa Marta (N lugares encontrados)» y orden: **Menor distancia, Menor afluencia, Calificación** (RF61 agrega precio).
- **Tarjetas:** imagen, nombre, descripción, calificación y reseñas, precio o rango, afluencia, distancia, accesibilidad, **Ver detalle**.
- **Paginación** numerada (RF16: 10 o menos por página).

## WF-06 — Detalle de lugar o servicio

Nombre; tipo, ubicación y calificación con reseñas; indicador de accesibilidad; etiquetas (Prestador verificado, Aforo en vivo, Reserva inmediata); favorito y compartir; galería (1 grande + 4 miniaturas); alerta de afluencia; descripción; información de contacto (teléfono, WhatsApp, dirección, correo, registro RNT); **Reportar información falsa o desactualizada** (RF57); tabla de precios; **Ver disponibilidad y reservar** (lleva a WF-10); horarios de atención (RF58); **Destinos alternativos con menor afluencia** (RF51, 3 tarjetas).

**Diferencias de la pantalla con WF-06 desde B1** (DEC-170): la dirección y el registro RNT del contacto muestran el valor del servicio (HC2), o «—» si no lo trae. «Horarios de atención» suma la **oferta inicial** («Días de operación» y, en Tour, «Salidas»), que no está en el wireframe (mejora de diseño: el turista la necesita para elegir fecha). La ubicación del subtítulo sigue deshabilitada: el contrato no tiene municipio.

## WF-07 — Asistente virtual IA

Encabezado «Asistente Virtual IA» y estado «En línea»; aviso de versión beta; conversación; panel «Itinerario sugerido» por día y hora con **Compartir** y **Descargar PDF**; «Prestadores mencionados»; **Guardar itinerario en perfil** (RF35) y **Ver en el mapa**; chips rápidos (Aforo en vivo, Rutas alternas, Tours mañana, Alquiler de equipos); campo de texto y enviar; sección «El asistente también puede ayudarte con» (Aforos en vivo, Rutas y clima, Prestadores verificados).

## WF-08 — Registrar servicio comercial

Migas: Panel del Prestador › Mis Servicios › Registrar Nuevo Servicio. Aviso de verificación (hasta 2 días hábiles). Secciones: **Datos del servicio** (nombre, categoría, RNT, descripción); **Datos de contacto** (teléfono/WhatsApp, correo, dirección, horario de atención); **Precio** («desde» en COP, «cobra por», casilla «el precio incluye impuestos»); **Campos según la categoría** (con Alojamiento: número de habitaciones, capacidad máxima, servicios incluidos WiFi / Desayuno / Piscina / Parqueadero); **Fotos** (1 a 5, la primera es la portada — RF48); **Condiciones de accesibilidad**; casilla de veracidad; **Publicar servicio**.

**Ampliación en Salt (iteración C, `WF-08-registrar-servicio.puml`; propuesta por arquitectura, pendiente de validación del equipo).** Conserva todos los elementos del PNG y agrega lo que piden RF09, RF10 y las decisiones de la iteración (CU: `docs/casos-de-uso/CU-registrar-servicio.md`):
- **Unidades del alojamiento** (de 1 a 20: nombre, tipo de habitación, habitaciones, huéspedes por habitación y precio por noche; DEC-133). El precio «desde» de un alojamiento se calcula (DEC-127). Resuelve la diferencia 1 en WF-08 y el «tipo de habitación» de la diferencia 5.
- **Oferta inicial** (DEC-136): días de operación en Tour y Alojamiento, y salidas (HH:mm) en Tour. Gastronomía y Alquiler no la tienen.
- **Dirección** obligatoria y **pin en el mapa** (DEC-132).
- El **RNT** se muestra en solo lectura: es el de la cuenta del prestador y el servicio guarda una copia (DEC-126; diferencia 5).
- **Accesibilidad** con las tres condiciones de RF27 (diferencia 3).
- «Cobra por», «incluye impuestos» y **Fotos**, deshabilitados: los dos primeros no están en el esquema (diferencia 5); las fotos del servicio dependen de RF48, descartado del plan del 60 % (DEC-54): la iteración E llevó las imágenes solo a los atractivos (DEC-55, ADR-009).
- **«Publicar servicio» pasa a «Enviar a aprobación»**: el servicio queda «Pendiente de aprobación» hasta que lo revise el Administrador (RF09, RF21).
- La casilla de veracidad se conserva; no tiene requisito en el SRS y no se guarda.

**Diferencias de la pantalla de B1 con WF-08** (`docs/trazabilidad.md`, DEC-161 a DEC-168):
- **Después del 201** la pantalla lleva al **detalle propio** del servicio (`/prestador/servicios/{id}`, WF-18) con «Enviado a aprobación» y «Pendiente de aprobación», no a la lista que indica el `.puml` (línea 100) (DEC-168): el Prestador ve lo que guardó el servidor, con el precio derivado y el RNT.
- El **RNT** aparece como «Se toma de tu registro de prestador», **sin el valor**: ningún endpoint se lo da al Prestador antes del registro (`/me` no lo trae). El valor se ve en el detalle propio y en WF-06 (DEC-167; pendiente de contrato). **Actualización de la iteración D (sesión B, O16, DEC-203):** el valor se muestra con el de `GET /api/v1/auth/prestador/perfil`; mientras carga, si falla o si no hay RNT, queda el texto anterior y el formulario sigue usable.
- La **casilla de veracidad** aparece deshabilitada (DEC-22): la «visita de verificación del municipio» del PNG no está en el SRS y exigirla sería una regla nueva (DEC-167).
- Se omiten la alerta del PNG (RUT, certificado de sanidad, cámara de comercio, «2 días hábiles») y la nota «Recibirás la respuesta en el correo registrado»: no están en el SRS y el aviso por correo es de RF34 o ms-notificaciones. Queda el aviso del `.puml` («Pendiente de aprobación»). «Un gestor del municipio verifica» pasa a «el Administrador» (RF21).
- Las migas empiezan en «Mis servicios»: no hay un «Panel del Prestador» aparte.
- **Mejoras de diseño:** «Latitud» y «Longitud» editables y «Usar el centro de Santa Marta» como alternativa al pin sin ratón (DEC-165), y «Todos los días» en la oferta (DEC-164).

## WF-18 — Mis servicios (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-18-mis-servicios.puml`), solo de estructura. Implementado en la sesión B1 (DEC-169). Flujo «Consultar mis servicios» del CU «Registrar servicio comercial» (DEC-129). Pantalla privada del Prestador.
- Lista de sus servicios (nombre, categoría, estado, motivo del rechazo y fecha de registro), del más reciente al más antiguo, con filtro de estado y paginación de 10; **Registrar nuevo servicio** lleva a WF-08.
- **Ver detalle** muestra los datos enviados y, si está Rechazado, el motivo. «Editar y reenviar» aparece deshabilitado (iteración D).

**Diferencias de la iteración D (sesión B) con WF-18** (DEC-196): en `Pendiente` y `Publicado`, el detalle propio muestra **Actualizar precio** (WF-20) y **Editar datos comerciales** (WF-21) y, al volver de una edición, su anuncio. En `Rechazado` no hay acciones de edición y «Editar y reenviar» sigue deshabilitado (DEC-193). El `.puml` no cambia.

## WF-19 — Aprobar servicios (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-19-aprobar-servicios.puml`), solo de estructura. Implementado en la sesión B2. CU «Aprobar o rechazar servicio» (RF21). Pantalla privada del Administrador.
- **Cola de pendientes** del más antiguo al más reciente (DEC-144), con filtro de estado y paginación de 10: nombre, categoría, RNT del prestador, si el prestador está activo y fecha de registro.
- **Revisar**: todos los datos del registro, la oferta y el estado de la cuenta del prestador; **Aprobar → Publicado** y **Rechazar**, que abre el diálogo con la **justificación obligatoria** (máximo 500 caracteres, con contador y error por campo; DEC-145).
- **Región de estado** con el 409 `ESTADO_CAMBIADO`: avisa y recarga (DEC-52).

**Diferencias de la pantalla de B2 con WF-19** (`docs/trazabilidad.md`, DEC-174 a DEC-181; el `.puml` no cambia):
- **Revisión de un servicio ya decidido:** la sección «Revisión» (fecha, revisor y, si se rechazó, el motivo) no está en el wireframe. Un `Publicado` o un `Rechazado` no muestra «Aprobar» ni «Rechazar» (DEC-179).
- **Campos por categoría:** el wireframe dibuja solo un Alojamiento. La pantalla muestra los datos de las cuatro categorías: las unidades y servicios incluidos de Alojamiento, los datos de Tour con sus salidas, y los de Gastronomía y Alquiler de equipos, que no tienen oferta (DEC-176).
- **Prestador:** se muestran el id, el RNT, el estado de la cuenta y «Prestador activo». No hay nombre: no está en `catalog_db` (DEC-144). Con el prestador no activo aparece un aviso de que el servicio quedará oculto aunque se apruebe (DEC-180).
- **Diálogo de rechazo:** suma el aviso de decisión definitiva, como el de WF-12 (DEC-177).
- **Cola:** las rutas son `/admin/servicios` y `/admin/servicios/:id`, a las que se entra con «Aprobar servicios» en el menú del avatar (DEC-174).
- **Mejoras de diseño:** las coordenadas también en texto junto al mapa, el foco en los títulos al cargar y al volver, y «Volver» conserva el filtro y la página de la cola.

## WF-20 — Actualizar precio (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-20-actualizar-precio.puml`), solo de estructura. Implementado en la iteración D, sesión B (DEC-196, DEC-198, DEC-200, DEC-201). CU «Actualizar tarifas» (RF32). Pantalla privada del Prestador, desde el detalle propio de WF-18.
- Tour, Gastronomía y Alquiler de equipos: el **precio «desde»**. Alojamiento: el **precio por noche de cada unidad** (nombre y tipo en solo lectura) y el «desde» derivado, que no se escribe. El cuerpo lleva todas las unidades y la `version` leída.
- Aviso de que no hace falta una nueva aprobación (en `Publicado`, el cambio se ve de inmediato) y de que la oferta y las reservas confirmadas no cambian.
- **409 `SERVICIO_MODIFICADO`:** avisa, relee el servicio, conserva lo escrito y marca «Valor actual» junto a lo que cambió; el reenvío es otro clic (DEC-200). **409 `ESTADO_CAMBIADO`:** vuelve al detalle con el aviso (DEC-201).

## WF-21 — Editar datos comerciales del servicio (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-21-datos-comerciales-servicio.puml`), solo de estructura. Implementado en la iteración D, sesión B (DEC-196, DEC-199). CU «Actualizar datos comerciales», flujo B (RF43). Pantalla privada del Prestador, desde el detalle propio de WF-18.
- **Nombre comercial** (el «nombre del establecimiento») y el **contacto** del servicio (teléfono y correo), con las reglas del registro; mismos avisos y 409 que WF-20.
- Una **nota** distingue este contacto del correo corporativo del perfil (WF-22) y del correo de acceso.

## WF-22 — Mi perfil comercial (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-22-perfil-comercial.puml`), solo de estructura. Implementado en la iteración D, sesión B (DEC-196, DEC-202). CU «Actualizar datos comerciales», flujo A (RF43). Pantalla privada del Prestador, desde el menú del avatar.
- **Solo lectura:** correo de acceso, representante legal, razón social y **RNT** (cierra O16). Nota: el correo de acceso es la credencial y no se cambia aquí (ADR-006).
- **Editable:** correo corporativo (contacto) y teléfono. El PUT envía solo esos dos campos.

## WF-23 — Historial de reservas del Prestador (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-23-historial-reservas-prestador.puml`), solo de estructura. Implementado en la iteración D, sesión B (DEC-196, DEC-204). CU «Consultar historial», RF12 del lado del Prestador. Pantalla privada del Prestador, desde el menú del avatar. Usa el lenguaje visual de WF-11.
- **Filtros en la URL** (DEC-169): desde y hasta (fecha de la actividad, como mucho 366 días), estado (uno o varios) y servicio. El selector lista los servicios del Prestador, hasta los **200 registrados más recientes**; si no carga, queda deshabilitado y el historial sigue.
- **Tarjetas** de 20 por página: servicio y categoría, unidad o salida, fechas, cantidades, precio unitario, total, estado y fechas de creación y de cancelación. **Ningún dato del Turista** y sin acciones.

## WF-09 — Registrar atractivo turístico

Migas: Inicio y búsqueda › Panel del gestor › Registrar atractivo. Secciones: **Datos del atractivo** (nombre, categoría, latitud, longitud); **Condiciones de accesibilidad**; **Aforo y estado operativo** (límite de capacidad de carga — RF07; estado Abierto / Cerrado temporalmente / En mantenimiento — RF33); **Fotos** (JPEG/PNG); **Normas y regulaciones ambientales** (máximo 1000 caracteres — RF24); casilla de veracidad; **Registrar atractivo**; consejos «Calcula el aforo», «Normas claras», «Actualiza el estado».

**Ampliación (`WF-09-registrar-atractivo.puml`; iteración E, sesión B1; propuesta por arquitectura, pendiente de validación del equipo).** CU «Registrar atractivo» y «Actualizar atractivo». Pantalla privada del Gestor.
- **Agrega:** la **descripción** (1 a 1000, RF06), el campo de la **capacidad de carga** (la imagen solo dibuja su etiqueta; de 1 a 100 000), el **horario de atención** (hasta 500, RF58), el **pin del mapa** sincronizado con la latitud y la longitud (DEC-223) y contadores en puntos de código (DEC-227).
- **Categoría:** `Naturaleza` o `Cultura` (DEC-214, DEC-234); la imagen dibuja «Sendero y naturaleza».
- **Accesibilidad:** las tres condiciones de RF27; la casilla «Señalización visual/auditiva» de la imagen se divide en dos (DEC-221). Resuelve la diferencia 3 para WF-09.
- **Fotos:** de 1 a 5, JPEG o PNG reconocidas por su firma, de 5 MB como máximo, con vista previa y «Quitar»; el error de cada una, junto a su miniatura (DEC-226).
- **Casilla de veracidad:** deshabilitada, sin marca de obligatoria (DEC-219, DEC-228).
- **Sin dirección:** RF06 admite «dirección o coordenadas según corresponda»; el registro usa las coordenadas.
- **Modo edición:** los mismos campos, salvo el estado (botón «Cambiar estado», con el diálogo de WF-24) y las fotos, que se ven en solo lectura con «Agregar o quitar fotos» deshabilitado (el contrato no lo permite, DEC-226).

## WF-24 — Atractivos, panel del gestor (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-24-atractivos-gestor.puml`), solo de estructura. Implementado en la iteración E, sesión B1 (DEC-222, DEC-225, DEC-233). Flujo «Consultar atractivos» del CU «Actualizar atractivo» y CU «Cambiar estado operativo» (RF07, RF33). Pantalla privada del Gestor, desde el menú del avatar («Atractivos») o desde «Panel del gestor» del pie.
- **Todos** los atractivos (no solo los que registró el Gestor, DEC-206), del más reciente al más antiguo, en páginas de 10 con la página en la URL.
- **Tabla** como WF-18 (por debajo de 40 rem cada fila es una tarjeta): foto, nombre, categoría, estado operativo (insignia), capacidad, fecha de registro y acciones «Editar», «Cambiar estado» y «Ver ficha pública».
- **Filtros** de estado y de texto, deshabilitados: el contrato no los admite (DEC-222, pendiente).
- **Diálogo «Cambiar estado»:** el estado actual no se puede elegir; con 409 `ATRACTIVO_MODIFICADO` se relee el atractivo y se avisa (DEC-225).

## WF-25 — Eventos, panel del gestor, y Registrar evento (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-25-eventos-gestor.puml`), solo de estructura. Implementado en la iteración E, sesión B2 (DEC-245, DEC-247 a DEC-249, DEC-251). CU «Registrar evento en agenda», con el flujo «Consultar eventos registrados» (RF25). Pantallas privadas del Gestor, desde el menú del avatar («Eventos» y «Registrar evento»).
- **Panel** (`/gestor/eventos`): **todos** los eventos, también los que ya terminaron, del más reciente al más antiguo (DEC-238), en páginas de 10 con la página en la URL. Tabla como WF-24 (por debajo de 40 rem, tarjetas): nombre, fechas (un día o el rango), ubicación y fecha de registro. **Sin acciones:** no hay edición ni borrado (DEC-215). Sin el Gestor (un uuid sin nombre) ni marca de «vencido», que exigiría calcular «hoy» en el cliente.
- **Registro** (`/gestor/eventos/nuevo`, página propia como WF-09): nombre (0/120), fecha de inicio y fecha de fin (selector de fecha), ubicación (0/200) y descripción (0/1000), con error por campo. El cliente valida los límites y que el fin no sea anterior al inicio; «no anterior a hoy» lo decide el servidor (DEC-242, DEC-248). Nota: se publica de inmediato y no se puede editar ni borrar. El 201 vuelve al panel con el anuncio.

## WF-26 — Agenda de eventos (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-26-agenda-eventos.puml`), solo de estructura. Implementado en la iteración E, sesión B2 (DEC-246, DEC-247, DEC-249). CU «Consultar agenda de eventos» (RF25). Pantalla pública (`/agenda`), sin token, desde «Agenda de eventos» del pie.
- **Tarjetas** en el orden del servidor (los vigentes según «hoy» en Bogotá, por fecha de inicio): bloque con el día y el mes de inicio, nombre, fechas, lugar y descripción con sus saltos de línea. Sin insignias de «Hoy» ni «En curso».
- **Estados:** agenda vacía con «Explorar la oferta turística»; página fuera de rango, a la última; error con «Reintentar». Páginas de 10.
- **Sin filtros** ni ficha propia de un evento: el CU los deja fuera de alcance.

## WF-06 para atractivos — Ficha pública de un atractivo (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo.** Wireframe en Salt (`WF-06-detalle-atractivo.puml`), solo de estructura. Implementado en la iteración E, sesión B1 (DEC-224). CU «Consultar detalle de atractivo». Pantalla pública (`/atractivos/:id`), sin token.
- Las regiones de WF-06 que tiene un atractivo: volver a la búsqueda, nombre, categoría, **insignia del estado operativo** (RF33), accesibilidad (RF27), corazón de favoritos, galería, descripción, **capacidad de carga** (RF07), **normas ambientales** (RF24) y **horario de atención** (RF58), los dos con sus saltos de línea, y el mapa de solo lectura.
- **Sin precio, contacto, RNT ni reserva:** un atractivo no se reserva.
- **Deshabilitados:** calificación (RF17), «Compartir», alerta de afluencia (RF39) y destinos alternativos (RF08); sin normas o sin horario, su región.

## WF-10 — Disponibilidad y reserva (nuevo)

Título «<Servicio> – Disponibilidad y reserva» con datos de verificación y RNT. **Buscar disponibilidad**: check-in, check-out, número de huéspedes y **Verificar disponibilidad en tiempo real** (RF11). Lista de unidades disponibles: imagen, tipo de alojamiento, disponibilidad, capacidad, etiquetas de servicios, estado de cupos, precio por noche y **Seleccionar habitación**; sin cupos, **Notificarme si se libera**. Panel **Resumen de reserva** (fechas, noches, huéspedes, precio × noches, impuestos, total), aviso de cancelación gratuita con 24 horas (RF44), **Confirmar reserva** (RF20) y «Información importante».

## WF-11 — Mis reservas (pendiente de diseño)

**Estado: pendiente de diseño.** No tiene imagen todavía: el diseño final lo hace quien hizo los wireframes y, cuando exista, la pantalla se ajusta a él. Mientras tanto, el prototipo usa el mismo lenguaje visual de WF-10 (tarjetas, tokens y botones). Se llega desde el menú del avatar («Mis reservas», solo para turistas).

Contenido: **lista de reservas** del turista, con el servicio y la unidad tal como se reservaron, las fechas, las cantidades y el total; **filtros** por rango de fechas de la actividad y por estado (RF12); el **estado** de cada reserva (Confirmada, Cancelada o Completada — RF52); y la acción **Cancelar**, con confirmación, solo en las Confirmadas y hasta 24 horas antes del inicio (RF18, RF44).

## WF-12 — Administrar cuentas (nuevo)

**Estado: propuesto por arquitectura, pendiente de validación del equipo (DEC-36).** No lo dibujó quien hizo los wireframes: es un wireframe en PlantUML Salt (`WF-12-administrar-cuentas.puml`, con su `.png` y su `.svg`). Cuando el equipo lo valide o lo rediseñe, la pantalla se ajusta a ese diseño. Pantalla privada del **Administrador**; se llega desde el menú del avatar. Cada diagrama del `.puml` se exporta a su propio archivo: `WF-12-administrar-cuentas` (la primera pantalla), `_001`, `_002` y `_003`. Los de RF45, `_004` (pestaña «Personal»), `_005` («Asignar rol» en «Turistas») y `_006` (diálogos de cambio de rol), los dibujó arquitectura con el estilo de los PNG del equipo de diseño y la pantalla implementada (DEC-268).

Contenido (CU «Administrar cuentas», `docs/casos-de-uso/CU-administrar-cuentas.md`):
- **Pestañas «Prestadores por validar (n)», «Prestadores» y «Turistas».** Cada una con buscador (nombre, correo o RNT en los prestadores; nombre o correo en los turistas), filtro de estado, tabla y paginación de 10 (DEC-41).
- **Por validar (RF28):** representante legal, RNT, correo corporativo y fecha de registro; **Revisar** abre el detalle del prestador (representante, razón social opcional, RNT, correo, teléfono, políticas aceptadas y estado) con **Aprobar → Activo** y **Rechazar**. Rechazar abre un diálogo con la **justificación obligatoria** (máximo 500 caracteres, con contador y error por campo; «Confirmar rechazo» deshabilitado si está vacía).
- **Prestadores (RF22):** **Inactivar** a un prestador Activo; **Ver motivo** en un Rechazado. «Reactivar» aparece **deshabilitado** en un Inactivo: RF22 no define la reactivación (DEC-37); queda como pendiente.
- **Turistas (RF62):** **Desactivar** y **Reactivar**.
- **Cambiar rol de cuenta (RF45, iteración B2; DEC-114):** tres diagramas más al final del `.puml` (`_004`, `_005` y `_006` al exportarlos), propuestos por arquitectura y pendientes de validación. Los implementa la sesión B de B2. CU: `docs/casos-de-uso/CU-cambiar-rol-cuenta.md`.
  - **Pestaña «Personal»:** Gestores y Administradores, con buscador (nombre o correo), filtro de rol, tabla y paginación de 10. Acciones: **Cambiar rol** (Gestor ↔ Administrador) y **Revocar** (vuelve a Turista; deshabilitado si la cuenta no tiene rol base). La propia cuenta del Administrador no tiene acciones.
  - **«Asignar rol»** en la pestaña «Turistas», solo para un turista Activo (deshabilitado si está Desactivado).
  - **Diálogo de confirmación** con el rol actual y el nuevo, y la advertencia de que la cuenta no usará sus reservas, favoritos ni historial mientras tenga el rol (DEC-112).
  - **Región de estado** con los 409 `ROL_CAMBIADO`, `CUENTA_NO_ELEGIBLE`, `ULTIMO_ADMINISTRADOR` y `CAMBIO_ROL_PROPIO`: avisa y recarga (DEC-52).
- **Confirmación** de Desactivar, Reactivar e Inactivar, **sin motivo** (DEC-38): solo el rechazo del prestador lo exige (RF28).
- **Región de estado** (`aria-live`): éxito, lista vacía y el 409 `ESTADO_CAMBIADO` («La cuenta cambió de estado; se recargó la lista», DEC-39).

Cambios respecto del contenido propuesto, por lo que el modelo guarda (DEC-45 y DEC-44):
- **Se quitó «País» del detalle del prestador:** RF02 no lo captura (solo el turista tiene país de origen, RF01).
- **«Políticas aceptadas» muestra «Sí · fecha»**, sin hora: el modelo guarda un booleano y el día del registro (decisión D6 de «Registrarse»).
- **El turista desactivado se muestra «Desactivado»**, no «Inactivo»: «Inactivo» es el estado de RF22, del prestador (DEC-44).

## WF-13, WF-14 y WF-15 — Contenido público (nuevos)

**Estado: propuestos por arquitectura, pendientes de validación del equipo (DEC-67, como DEC-36).**
- No los dibujó quien hizo los wireframes. Son wireframes en PlantUML Salt, solo de estructura (regiones, elementos y flujo).
- Cada `.puml` tiene un `.png` con el mismo nombre, dibujado por arquitectura con el estilo de los PNG del equipo de diseño y la pantalla implementada (DEC-268). El `.puml` sigue siendo la referencia de estructura.
- Los implementa la sesión B de la iteración «Contenido público».
- El contenido de las preguntas y los avisos va en el idioma en que lo escribe el Administrador; la interfaz, en ES y EN (DEC-69).

**WF-13 — Preguntas frecuentes** (CU «Gestionar FAQs», `docs/casos-de-uso/CU-gestionar-faqs.md`; RF26, RF47):
- **Sección pública,** desde «Preguntas frecuentes» del pie:
  - título y texto de apoyo;
  - **barra de búsqueda** que filtra en tiempo real por título o cuerpo: coincidencia parcial, sin distinguir mayúsculas ni tildes (RF47, DEC-62);
  - lista de preguntas en acordeón, por título en orden alfabético (DEC-63);
  - estado «sin coincidencias» con «Borrar búsqueda», y estado de lista vacía.
- **Gestión del Administrador:**
  - contador «n de 200 preguntas» y **Nueva pregunta**;
  - tabla con título, fecha de actualización, **Editar** y **Eliminar**;
  - formulario con **Título** (contador 0/150) y **Cuerpo** (contador 0/2000), con error por campo;
  - confirmación de **Eliminar** («no se puede deshacer»);
  - región de estado (`aria-live`): éxito, 404 y 409 `LIMITE_FAQ`.

**WF-14 — Avisos generales** (CU «Crear y publicar aviso general», `docs/casos-de-uso/CU-crear-publicar-aviso-general.md`; RF40):
- **Banner** (elemento común): todos los avisos activos, del más reciente al más antiguo, con su tipo (Clima, Cierre o Comunicado) y fecha.
  - **Cerrar avisos** lo oculta en la sesión del navegador.
  - Sin avisos activos, no se muestra.
- **Gestión del Administrador:**
  - formulario **Nuevo aviso** con **Mensaje** (contador 0/300), **Tipo** y **Publicar**;
  - listado de 10 por página con mensaje, tipo, fecha, estado y **Despublicar** (solo en los activos), con confirmación;
  - región de estado: éxito, 409 (ya despublicado) y 404.
  - No hay editar ni borrar (DEC-64).

**WF-15 — Términos y condiciones** (CU «Consultar términos y condiciones», `docs/casos-de-uso/CU-consultar-terminos.md`; RF46):
- Página estática, desde el pie (y el pie reducido y el texto legal de WF-01), en ES y EN.
- Título y nota «Texto del prototipo académico — Universidad del Magdalena. No es un documento legal vigente».
- Índice con enlaces a las 9 secciones de DEC-68. La sección 4, «Datos personales (Ley 1581 de 2012)», es el destino de «Políticas de privacidad» (pie y casilla del registro).

## WF-16, WF-17 y los cambios en WF-05, WF-06 y WF-11 — Turista (nuevos)

**Estado: propuestos por arquitectura, pendientes de validación del equipo (DEC-90, como DEC-36 y DEC-67).**
- No los dibujó quien hizo los wireframes. Son wireframes en PlantUML Salt, solo de estructura (regiones, elementos y flujo).
- Cada `.puml` tiene un `.png` con el mismo nombre, dibujado por arquitectura con el estilo de los PNG del equipo de diseño y la pantalla implementada (DEC-268). El `.puml` sigue siendo la referencia de estructura.
- Los implementa la sesión B de la iteración B1 «Turista» (DEC-83). Son solo para el rol Turista.

**WF-16 — Perfil del turista** (CU «Actualizar datos personales» y «Actualizar intereses turísticos», `docs/casos-de-uso/CU-actualizar-perfil.md`; RF53):
- Se llega desde el menú del avatar («Mi perfil»).
- **Datos personales:**
  - correo en solo lectura (no se edita, DEC-79);
  - primer y segundo nombre, primer y segundo apellido (los segundos, opcionales) y país de origen, con las reglas de WF-02 (RF01);
  - **Guardar cambios** y **Descartar**, con error por campo.
- **Intereses turísticos:**
  - casillas Naturaleza, Cultura y Aventura; al menos una (DEC-85);
  - **Guardar intereses**.
- Acceso a **Mis favoritos** (WF-17) y región de estado (`aria-live`).

**WF-17 — Mis favoritos** (CU «Guardar favoritos», `docs/casos-de-uso/CU-guardar-favoritos.md`; RF54):
- Contador «n de 100 favoritos» (DEC-86), del más reciente al más antiguo.
- Cada favorito: imagen, nombre, categoría, insignia **No disponible** (servicio despublicado o de un prestador inactivo), **Ver detalle** y **Quitar**.
  - «Ver detalle» en los favoritos disponibles: la ficha del servicio (WF-06) o, desde la iteración E, la del atractivo (DEC-224).
- Estado vacío con **Explorar la oferta**.

**Cambios en pantallas existentes** (`WF-05-06-11-cambios-turista.puml`):
- **WF-05 (RF60, RF54):**
  - región **Últimas búsquedas**, solo con la sesión de un turista: hasta 10, la más reciente primero; un clic repite la búsqueda. Si la búsqueda usaba la ubicación, el frontend la vuelve a pedir (RF38);
  - **corazón** en cada tarjeta, de servicio o de atractivo.
- **WF-06 (RF54):** el corazón de la ficha pasa a estar activo. El ícono **Compartir** de la ficha **sigue deshabilitado**: RF49 solo traza el botón de las reservas Completadas de WF-11.
- **WF-11 (RF49):** botón **Compartir** solo en las reservas Completadas. Comparte el enlace `/servicios/:idServicio` con la hoja de compartir del sistema o, si no existe, lo copia al portapapeles (DEC-81).

## Diferencias detectadas en la v2

Se detectaron antes de implementar las pantallas; cada punto dice cómo quedó. El estado detallado está en `docs/trazabilidad.md` («Cambios de los wireframes v2»).

1. **WF-10 frente al modelo de datos (crítico):** la pantalla muestra varias unidades por servicio (p. ej. «Cabaña Campestre Familiar», «Cabaña Mirador del Mar»), cada una con su precio, capacidad, servicios y cupos. El modelo tenía un solo `tipoHabitacion` por servicio en `catalog_db` e `Inventario_Cupo` por servicio y fecha en `reservas_db`. **Resuelta a favor del wireframe** (D1 de «Reservar servicio», `docs/trazabilidad.md`): un alojamiento tiene una o más unidades, cada una con tipo, habitaciones, capacidad y precio por noche (`catalog_db`, migración M001); `inventario_cupo` de ms-booking lleva cupos por unidad y noche. Queda abierto: las etiquetas de servicios son del alojamiento, no de cada unidad. **WF-08 resuelto en la iteración C (B1):** el formulario admite de 1 a 20 unidades (`EditorUnidades`, DEC-133, DEC-164).
2. **Pagos en WF-10:** «Pago seguro con tarjeta, PSE o Nequi» no tiene requisito en el SRS ni componente en la arquitectura. «Notificarme si se libera» tampoco tiene requisito. **Se omiten** en la pantalla (`docs/trazabilidad.md`, «Pendientes», WF-10).
3. **Vocabulario de accesibilidad:** RF27 y RF50 definen tres condiciones (movilidad reducida, visual, auditiva). WF-08 usa otras cuatro (rampas, silla de ruedas, braille, baños adaptados) y WF-09 otras dos. Hay que unificarlas en un solo vocabulario. **Resuelta:** WF-08 usa las tres de RF27 (DEC-131) y WF-09 también, con la casilla «Señalización visual/auditiva» dividida en dos (DEC-221, iteración E).
4. **Vocabulario de servicios incluidos:** WF-08 ofrece WiFi, Desayuno, Piscina y Parqueadero; WF-10 muestra además Aire acondicionado y Baño privado. Debe existir una sola lista cerrada. **Abierta:** el prototipo usa la lista de WF-08 como vocabulario cerrado (validador de MongoDB y «Más filtros»); la lista definitiva sigue por decidir.
5. **WF-08 frente a RF10 y al esquema `servicios`:** falta el campo obligatorio «tipo de habitación» (con las unidades de la diferencia 1, cada unidad lleva el suyo); «cobra por» e «incluye impuestos» no existen en el esquema; el RNT pertenece al prestador (`auth_db`), no a cada servicio. **Resuelta en parte:** el tipo de habitación va en cada unidad (DEC-133) y el servicio guarda una copia del RNT de la cuenta (DEC-126); «cobra por» e «incluye impuestos» siguen deshabilitados (DEC-167).
6. **«Aforo» frente a «afluencia»:** los wireframes usan «aforo» para el indicador de RF39; el prototipo usa «afluencia», como el SRS.
