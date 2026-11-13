// Samaria360 - lo ejecuta aplicar.sh (servicio mongo-migraciones) después de las migraciones, en cada arranque.
// Mínimo privilegio (DEC-275): ms-catalog se conecta con su propio usuario, con readWrite solo sobre catalog_db, y no
// con root. El usuario vive en la base admin (authSource=admin, como root). Si ya existe, se le vuelven a fijar la
// contraseña y los roles: un cambio en .env llega con el siguiente arranque. Las migraciones siguen corriendo con root,
// porque instalar validadores (collMod) exige más que readWrite.
const usuario = process.env.CATALOG_DB_USER;
const clave = process.env.CATALOG_DB_PASSWORD;
if (!usuario || !clave) {
  throw new Error('Faltan CATALOG_DB_USER o CATALOG_DB_PASSWORD');
}
const admin = db.getSiblingDB('admin');
const roles = [{ role: 'readWrite', db: 'catalog_db' }];
if (admin.getUser(usuario)) {
  admin.updateUser(usuario, { pwd: clave, roles });
  print(`Usuario ${usuario} al día: readWrite en catalog_db`);
} else {
  admin.createUser({ user: usuario, pwd: clave, roles });
  print(`Usuario ${usuario} creado: readWrite en catalog_db`);
}
