// Samaria360 - usuario propio de ms-chatbot (como DEC-275): readWrite solo sobre chatbot_db, en la base admin. Si ya
// existe, se le vuelven a fijar la contraseña y los roles. Lo ejecuta aplicar.sh (mongo-migraciones) en cada arranque.
const usuario = process.env.CHATBOT_DB_USER;
const clave = process.env.CHATBOT_DB_PASSWORD;
if (!usuario || !clave) {
  throw new Error("Faltan CHATBOT_DB_USER o CHATBOT_DB_PASSWORD");
}
const admin = db.getSiblingDB("admin");
const roles = [{ role: "readWrite", db: "chatbot_db" }];
if (admin.getUser(usuario)) {
  admin.updateUser(usuario, { pwd: clave, roles });
  print(`Usuario ${usuario} al día: readWrite en chatbot_db`);
} else {
  admin.createUser({ user: usuario, pwd: clave, roles });
  print(`Usuario ${usuario} creado: readWrite en chatbot_db`);
}
