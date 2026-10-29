-- Los usuarios inician sesion con Microsoft Entra ID (tenant theclassflow.onmicrosoft.com).
-- ExternalIdentityService vincula la primera vez por correo, asi que el correo interno debe
-- coincidir con el UPN de Entra: nombre.apellido@theclassflow.onmicrosoft.com.
UPDATE users
SET email = REPLACE(email, '@classflow.cl', '@theclassflow.onmicrosoft.com')
WHERE email LIKE '%@classflow.cl';
