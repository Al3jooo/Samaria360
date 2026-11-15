package co.edu.unimagdalena.samaria360.catalog.domain.model;

import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosCoordenadas;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosDetalle;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosOferta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosUnidad;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroAtractivo.ImagenPorGuardar;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaImagen;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * CU «Registrar servicio comercial» (RF09, RF10, RF37; parte de RF27 y RF58): valida un {@link BorradorServicio}
 * y crea el servicio Pendiente (DEC-124). Reúne un motivo por campo, con el nombre JSON (los anidados con punto e
 * índice, p. ej. {@code detalleCategoria.unidades[0].precioNoche}, DEC-151), y lanza
 * {@link ContenidoInvalidoException} si alguno no sirve; las reglas por campo están en {@link CamposServicio} (DEC-138).
 * Los máximos numéricos son técnicos, no reglas de negocio: el precio cabe en
 * {@code reserva.precio_unitario numeric(10,2)} de ms-booking.
 */
public final class RegistroServicio {

    public static final int DESCRIPCION_MAXIMA = 2000;
    public static final int TELEFONO_MAXIMO = 20;
    public static final int CORREO_MAXIMO = 254;
    public static final int TEXTO_LARGO_MAXIMO = 500;
    public static final int UNIDADES_MAXIMAS = 20;
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("99999999.99");
    public static final BigDecimal DURACION_MAXIMA = new BigDecimal("720");
    public static final int AFORO_MAXIMO = 10_000;

    private static final Pattern HORA = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    private final CamposServicio campos = new CamposServicio();

    /** El servicio nuevo y las imágenes que hay que guardar antes de insertarlo, en el orden recibido (DEC-318). */
    public record Registro(ServicioComercial servicio, List<ImagenPorGuardar> imagenes) {
    }

    private RegistroServicio() {
    }

    /**
     * @param id              ObjectId del servicio nuevo
     * @param idsUnidad       genera el {@code idUnidad} de cada unidad (DEC-141)
     * @param rnt             copia del RNT de la proyección {@code cuentas} (DEC-126)
     * @param prestadorActivo la copia del estado del prestador, también de la proyección (DEC-125)
     * @throws ContenidoInvalidoException con un motivo por cada campo inválido
     */
    public static ServicioComercial registrar(BorradorServicio borrador, String id, Supplier<String> idsUnidad,
                                              UUID prestadorId, String rnt, boolean prestadorActivo,
                                              Instant fechaRegistro) {
        return new RegistroServicio().validar(borrador, null, id, idsUnidad, () -> {
            throw new IllegalStateException("Sin imágenes no se generan ids de imagen");
        }, prestadorId, rnt, prestadorActivo, fechaRegistro).servicio();
    }

    /**
     * Registro con fotos (WF-08, DEC-318): de 1 a 5 imágenes JPEG o PNG de hasta 5 MB, con las reglas de la galería
     * (RF48). Los errores de las imágenes ({@code imagenes}, {@code imagenes[i]}) van en el mismo 400 que los de los
     * datos.
     *
     * @param idsImagen genera el id de cada imagen; solo se llama si todo es válido
     * @throws ContenidoInvalidoException con un motivo por cada campo inválido
     */
    public static Registro registrarConImagenes(BorradorServicio borrador, List<ImagenSubida> imagenes, String id,
                                                Supplier<String> idsUnidad, Supplier<String> idsImagen,
                                                UUID prestadorId, String rnt, boolean prestadorActivo,
                                                Instant fechaRegistro) {
        return new RegistroServicio().validar(borrador, imagenes == null ? List.of() : imagenes, id, idsUnidad,
                idsImagen, prestadorId, rnt, prestadorActivo, fechaRegistro);
    }

    /** {@code imagenes} nulo: el registro por JSON, sin fotos (anterior a DEC-318). */
    private Registro validar(BorradorServicio borrador, List<ImagenSubida> imagenes, String id,
                             Supplier<String> idsUnidad, Supplier<String> idsImagen, UUID prestadorId, String rnt,
                             boolean prestadorActivo, Instant fechaRegistro) {
        BorradorServicio b = borrador == null
                ? new BorradorServicio(null, null, null, null, null, null, null, null, null, null, null) : borrador;
        String nombre = campos.texto("nombreComercial", b.nombreComercial(), ServicioComercial.LONGITUD_MAXIMA_NOMBRE);
        Categoria categoria = categoria(b.categoria());
        String descripcion = campos.texto("descripcion", b.descripcion(), DESCRIPCION_MAXIMA);
        String direccion = campos.texto("direccion", b.direccion(), ServicioComercial.LONGITUD_MAXIMA_DIRECCION);
        Coordenada coordenada = coordenada(b.coordenadas());
        Contacto contacto = campos.contacto(b.contacto());
        Set<CondicionAccesibilidad> accesibilidad = accesibilidad(b.condicionesAccesibilidad());
        String horario = campos.textoOpcional("horarioAtencion", b.horarioAtencion(), TEXTO_LARGO_MAXIMO);
        DetalleCategoria detalle = null;
        BigDecimal precio = null;
        Oferta oferta = null;
        if (categoria != null) {
            detalle = detalle(categoria, b.detalleCategoria(), idsUnidad);
            // En Alojamiento el precio «desde» se deriva de las unidades (DEC-127): el recibido se ignora.
            precio = categoria == Categoria.Alojamiento ? null
                    : campos.decimal("precioBase", b.precioBase(), PRECIO_MAXIMO);
            oferta = oferta(categoria, b.oferta());
        }
        if (imagenes != null) {
            validarImagenes(imagenes);
        }
        campos.lanzarSiHayErrores();
        List<ImagenPorGuardar> porGuardar = new ArrayList<>();
        List<String> rutas = new ArrayList<>();
        for (ImagenSubida imagen : imagenes == null ? List.<ImagenSubida>of() : imagenes) {
            ImagenPorGuardar guardar = new ImagenPorGuardar(idsImagen.get(),
                    FormatoImagen.detectar(imagen.contenido()).orElseThrow(), imagen.nombre(), imagen.contenido());
            porGuardar.add(guardar);
            rutas.add(RegistroAtractivo.RUTA_IMAGENES + guardar.id());
        }
        return new Registro(new ServicioComercial(id, prestadorId, nombre, categoria, detalle, descripcion, coordenada,
                contacto, EstadoPublicacion.Pendiente, prestadorActivo, accesibilidad, horario, precio, rutas, direccion,
                oferta, rnt, fechaRegistro, null), porGuardar);
    }

    /** Las mismas reglas que las fotos de un atractivo y la galería (RF48): cantidad y cada archivo. */
    private void validarImagenes(List<ImagenSubida> imagenes) {
        PoliticaImagen.motivoCantidad(imagenes.size()).ifPresent(motivo -> campos.agregar("imagenes", motivo));
        if (imagenes.size() > PoliticaImagen.MAXIMO) {
            return;
        }
        for (int i = 0; i < imagenes.size(); i++) {
            String campo = "imagenes[" + i + "]";
            PoliticaImagen.motivo(imagenes.get(i)).ifPresent(motivo -> campos.agregar(campo, motivo));
        }
    }

    // ------------------------------------------------------------------ datos generales (RF09)

    private Categoria categoria(String valor) {
        if (valor == null || valor.isBlank()) {
            campos.agregar("categoria", MotivoInvalido.REQUERIDO);
            return null;
        }
        Categoria categoria = valorDe(Categoria.class, valor);
        if (categoria == null) {
            campos.agregar("categoria", MotivoInvalido.NO_PERMITIDO);
        }
        return categoria;
    }

    private Coordenada coordenada(DatosCoordenadas datos) {
        if (datos == null) {
            campos.agregar("coordenadas", MotivoInvalido.REQUERIDO);
            return null;
        }
        boolean longitud = enRango("coordenadas.longitud", datos.longitud(), 180);
        boolean latitud = enRango("coordenadas.latitud", datos.latitud(), 90);
        return longitud && latitud ? new Coordenada(datos.longitud(), datos.latitud()) : null;
    }

    /** DEC-137: solo el rango global; el SRS no fija un límite geográfico. */
    private boolean enRango(String campo, Double valor, double limite) {
        if (valor == null) {
            campos.agregar(campo, MotivoInvalido.REQUERIDO);
        } else if (valor < -limite) {
            campos.agregar(campo, MotivoInvalido.VALOR_MINIMO);
        } else if (valor > limite) {
            campos.agregar(campo, MotivoInvalido.VALOR_MAXIMO);
        } else {
            return true;
        }
        return false;
    }

    /** RF27: el vocabulario cerrado del SRS, sin repetir. */
    private Set<CondicionAccesibilidad> accesibilidad(List<String> valores) {
        Set<CondicionAccesibilidad> condiciones = EnumSet.noneOf(CondicionAccesibilidad.class);
        if (valores == null) {
            return condiciones;
        }
        for (int i = 0; i < valores.size(); i++) {
            String campo = "condicionesAccesibilidad[" + i + "]";
            CondicionAccesibilidad condicion = valorDe(CondicionAccesibilidad.class, valores.get(i));
            if (condicion == null) {
                campos.agregar(campo, MotivoInvalido.NO_PERMITIDO);
            } else if (!condiciones.add(condicion)) {
                campos.agregar(campo, MotivoInvalido.DUPLICADO);
            }
        }
        return condiciones;
    }

    // ------------------------------------------------------------------ campos por categoría (RF10)

    private DetalleCategoria detalle(Categoria categoria, DatosDetalle d, Supplier<String> idsUnidad) {
        if (d == null) {
            campos.agregar("detalleCategoria", MotivoInvalido.REQUERIDO);
            return null;
        }
        int antes = campos.cantidad();
        return switch (categoria) {
            case Alojamiento -> alojamiento(d, idsUnidad);
            case Gastronomia -> {
                String tipoCocina = campos.texto("detalleCategoria.tipoCocina", d.tipoCocina(), 100);
                Integer aforo = campos.entero("detalleCategoria.aforoMaxTurno", d.aforoMaxTurno(), 1, AFORO_MAXIMO);
                String rango = campos.texto("detalleCategoria.rangoPrecioPlato", d.rangoPrecioPlato(), 50);
                yield campos.cantidad() > antes ? null
                        : new DetalleGastronomia(tipoCocina, aforo, d.menuVegetariano(), rango);
            }
            case Tour -> {
                String punto = campos.texto("detalleCategoria.puntoEncuentro", d.puntoEncuentro(), 200);
                BigDecimal duracion = campos.decimal("detalleCategoria.duracionHoras", d.duracionHoras(),
                        DURACION_MAXIMA);
                String dificultad = campos.textoOpcional("detalleCategoria.nivelDificultad", d.nivelDificultad(), 50);
                Integer aforo = campos.entero("detalleCategoria.aforoMaxGrupo", d.aforoMaxGrupo(), 1, AFORO_MAXIMO);
                yield campos.cantidad() > antes ? null : new DetalleTour(punto, duracion, dificultad, aforo);
            }
            case AlquilerEquipo -> {
                String tipo = campos.texto("detalleCategoria.tipoVehiculoEquipo", d.tipoVehiculoEquipo(), 100);
                Integer unidades = campos.entero("detalleCategoria.unidadesDisponibles", d.unidadesDisponibles(), 1,
                        AFORO_MAXIMO);
                String condiciones = campos.textoOpcional("detalleCategoria.condicionesAlquiler",
                        d.condicionesAlquiler(), TEXTO_LARGO_MAXIMO);
                yield campos.cantidad() > antes ? null : new DetalleAlquilerEquipo(tipo, unidades, condiciones);
            }
        };
    }

    /** DEC-133: de 1 a 20 unidades; el {@code idUnidad} lo genera el servidor. */
    private DetalleAlojamiento alojamiento(DatosDetalle d, Supplier<String> idsUnidad) {
        int antes = campos.cantidad();
        List<DatosUnidad> datos = d.unidades();
        List<UnidadAlojamiento> unidades = new ArrayList<>();
        if (datos == null) {
            campos.agregar("detalleCategoria.unidades", MotivoInvalido.REQUERIDO);
        } else if (datos.isEmpty()) {
            campos.agregar("detalleCategoria.unidades", MotivoInvalido.CANTIDAD_MINIMA);
        } else if (datos.size() > UNIDADES_MAXIMAS) {
            campos.agregar("detalleCategoria.unidades", MotivoInvalido.CANTIDAD_MAXIMA);
        } else {
            for (int i = 0; i < datos.size(); i++) {
                UnidadAlojamiento unidad = unidad("detalleCategoria.unidades[" + i + "]", datos.get(i), idsUnidad);
                if (unidad != null) {
                    unidades.add(unidad);
                }
            }
        }
        List<String> incluidos = serviciosIncluidos(d.serviciosIncluidos());
        return campos.cantidad() > antes ? null : new DetalleAlojamiento(unidades, incluidos);
    }

    private UnidadAlojamiento unidad(String prefijo, DatosUnidad u, Supplier<String> idsUnidad) {
        if (u == null) {
            campos.agregar(prefijo, MotivoInvalido.REQUERIDO);
            return null;
        }
        int antes = campos.cantidad();
        String nombre = campos.texto(prefijo + ".nombre", u.nombre(), UnidadAlojamiento.LONGITUD_MAXIMA_NOMBRE);
        String tipo = campos.texto(prefijo + ".tipoHabitacion", u.tipoHabitacion(), 50);
        Integer habitaciones = campos.entero(prefijo + ".numHabitaciones", u.numHabitaciones(), 1, 1000);
        Integer capacidad = campos.entero(prefijo + ".capacidadMaxHuespedes", u.capacidadMaxHuespedes(), 1, 50);
        BigDecimal precio = campos.decimal(prefijo + ".precioNoche", u.precioNoche(), PRECIO_MAXIMO);
        return campos.cantidad() > antes ? null
                : new UnidadAlojamiento(idsUnidad.get(), nombre, tipo, habitaciones, capacidad, precio);
    }

    /** RF10: el vocabulario cerrado de WF-08, sin distinguir mayúsculas y sin repetir. */
    private List<String> serviciosIncluidos(List<String> valores) {
        List<String> incluidos = new ArrayList<>();
        if (valores == null) {
            return incluidos;
        }
        for (int i = 0; i < valores.size(); i++) {
            String campo = "detalleCategoria.serviciosIncluidos[" + i + "]";
            try {
                String nombre = ServicioIncluido.desdeTexto(valores.get(i)).name();
                if (incluidos.contains(nombre)) {
                    campos.agregar(campo, MotivoInvalido.DUPLICADO);
                } else {
                    incluidos.add(nombre);
                }
            } catch (IllegalArgumentException e) {
                campos.agregar(campo, MotivoInvalido.NO_PERMITIDO);
            }
        }
        return incluidos;
    }

    // ------------------------------------------------------------------ oferta inicial (DEC-130, DEC-136)

    private Oferta oferta(Categoria categoria, DatosOferta datos) {
        if (categoria == Categoria.Gastronomia || categoria == Categoria.AlquilerEquipo) {
            if (datos != null) {
                campos.agregar("oferta", MotivoInvalido.NO_PERMITIDO);
            }
            return null;
        }
        if (datos == null) {
            campos.agregar("oferta", MotivoInvalido.REQUERIDO);
            return null;
        }
        int antes = campos.cantidad();
        List<DiaSemana> dias = dias(datos.diasOperacion());
        List<LocalTime> salidas = List.of();
        if (categoria == Categoria.Tour) {
            salidas = salidas(datos.salidas());
        } else if (datos.salidas() != null && !datos.salidas().isEmpty()) {
            campos.agregar("oferta.salidas", MotivoInvalido.NO_PERMITIDO);
        }
        return campos.cantidad() > antes ? null : new Oferta(dias, salidas);
    }

    private List<DiaSemana> dias(List<String> valores) {
        List<DiaSemana> dias = new ArrayList<>();
        if (valores == null || valores.isEmpty()) {
            campos.agregar("oferta.diasOperacion", MotivoInvalido.REQUERIDO);
            return dias;
        }
        for (int i = 0; i < valores.size(); i++) {
            String campo = "oferta.diasOperacion[" + i + "]";
            DiaSemana dia = valorDe(DiaSemana.class, valores.get(i));
            if (dia == null) {
                campos.agregar(campo, MotivoInvalido.NO_PERMITIDO);
            } else if (dias.contains(dia)) {
                campos.agregar(campo, MotivoInvalido.DUPLICADO);
            } else {
                dias.add(dia);
            }
        }
        return dias;
    }

    private List<LocalTime> salidas(List<String> valores) {
        List<LocalTime> salidas = new ArrayList<>();
        if (valores == null || valores.isEmpty()) {
            campos.agregar("oferta.salidas", MotivoInvalido.REQUERIDO);
            return salidas;
        }
        if (valores.size() > Oferta.MAXIMO_SALIDAS) {
            campos.agregar("oferta.salidas", MotivoInvalido.CANTIDAD_MAXIMA);
            return salidas;
        }
        Set<LocalTime> vistas = new HashSet<>();
        for (int i = 0; i < valores.size(); i++) {
            String campo = "oferta.salidas[" + i + "]";
            String valor = valores.get(i);
            if (valor == null || !HORA.matcher(valor).matches()) {
                campos.agregar(campo, MotivoInvalido.FORMATO);
            } else if (!vistas.add(LocalTime.parse(valor))) {
                campos.agregar(campo, MotivoInvalido.DUPLICADO);
            } else {
                salidas.add(LocalTime.parse(valor));
            }
        }
        return salidas;
    }

    // ------------------------------------------------------------------ auxiliares

    private static <E extends Enum<E>> E valorDe(Class<E> tipo, String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
