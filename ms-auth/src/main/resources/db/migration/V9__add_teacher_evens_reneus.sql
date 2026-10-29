-- Docente invitado (B2B) en el tenant theclassflow.onmicrosoft.com.
-- UPN en Entra: e.reneus_duocuc.cl#EXT#@theclassflow.onmicrosoft.com
-- Para usuarios invitados, los claims preferred_username/email del token traen el correo
-- de origen, no el UPN #EXT#, por eso se registra ese correo para la vinculacion inicial.
-- id_number es provisorio: reemplazar por el RUT real.
-- La contrasena no se usa con el perfil entra (login local deshabilitado); hash BCrypt de "password".
INSERT INTO users (first_name, last_name, id_number, email, password, role, course, guardian_id, active)
VALUES
('Evens', 'Reneus', '23123456', 'e.reneus@duocuc.cl',
 '$2a$10$x6PM8Civ0UeHrBjAsdkUiuc7tJlnRpoxj1fR7Mhvpp7Jz/StTevrO', 'TEACHER', NULL, NULL, TRUE);
