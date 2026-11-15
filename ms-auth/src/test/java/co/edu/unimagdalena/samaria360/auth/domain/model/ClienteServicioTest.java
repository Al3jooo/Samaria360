package co.edu.unimagdalena.samaria360.auth.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Credenciales de los clientes internos (DEC-280). */
class ClienteServicioTest {

    @Test
    void aceptaSoloElSecretoConfigurado() {
        ClienteServicio cliente = new ClienteServicio("ms-analytics", "s3cr3to", Rol.Prestador);

        assertThat(cliente.acepta("s3cr3to")).isTrue();
        assertThat(cliente.acepta("s3cr3to ")).isFalse();
        assertThat(cliente.acepta(null)).isFalse();
    }

    @Test
    void sinSecretoQuedaDeshabilitado() {
        assertThat(new ClienteServicio("ms-booking", "", Rol.Turista).acepta("")).isFalse();
        assertThat(new ClienteServicio("ms-booking", null, Rol.Turista).acepta("x")).isFalse();
    }

    @Test
    void niElSecretoNiElContactoVanAlToString() {
        assertThat(new ClienteServicio("ms-analytics", "s3cr3to", Rol.Prestador).toString()).doesNotContain("s3cr3to");
        assertThat(new Contacto(java.util.UUID.randomUUID(), Rol.Turista, "Ana", "ana@x.com", null).toString())
                .doesNotContain("ana@x.com").doesNotContain("Ana");
    }
}
