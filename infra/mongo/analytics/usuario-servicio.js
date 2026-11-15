// Samaria360 - usuario propio de ms-analytics (como DEC-275): readWrite solo sobre analytics_db, en la base admin. Si ya
// existe, se le vuelven a fijar la contraseña y los roles. Lo ejecuta aplicar.sh (mongo-migraciones) en cada arranque.
const usuario = process.env.ANALYTICS_DB_USER;
const clave = process.env.ANALYTICS_DB_PASSWORD;
if (!usuario || !clave) {
  throw new Error("Faltan ANALYTICS_DB_USER o ANALYTICS_DB_PASSWORD");
}
const admin = db.getSiblingDB("admin");
const roles = [{ role: "readWrite", db: "analytics_db" }];
if (admin.getUser(usuario)) {
  admin.updateUser(usuario, { pwd: clave, roles });
  print(`Usuario ${usuario} al día: readWrite en analytics_db`);
} else {
  admin.createUser({ user: usuario, pwd: clave, roles });
  print(`Usuario ${usuario} creado: readWrite en analytics_db`);
}
