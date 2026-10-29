-- V3 asigno siete evaluaciones de ejemplo a la asignatura contigua del mismo curso
-- (p. ej. "Prueba Geometria" quedo en Historia). Se reasignan buscando la asignatura
-- por nombre de curso y de asignatura, sin depender de los ids generados.

UPDATE evaluations SET subject_id = (
    SELECT s.id FROM subjects s JOIN courses c ON c.id = s.course_id
    WHERE c.name = '5° Básico' AND s.name = 'Inglés')
WHERE name = 'Prueba Inglés';

UPDATE evaluations SET subject_id = (
    SELECT s.id FROM subjects s JOIN courses c ON c.id = s.course_id
    WHERE c.name = '1° Medio' AND s.name = 'Matemáticas')
WHERE name = 'Prueba Álgebra';

UPDATE evaluations SET subject_id = (
    SELECT s.id FROM subjects s JOIN courses c ON c.id = s.course_id
    WHERE c.name = '1° Medio' AND s.name = 'Ciencias')
WHERE name = 'Prueba Química';

UPDATE evaluations SET subject_id = (
    SELECT s.id FROM subjects s JOIN courses c ON c.id = s.course_id
    WHERE c.name = '2° Medio' AND s.name = 'Matemáticas')
WHERE name = 'Prueba Geometría';

UPDATE evaluations SET subject_id = (
    SELECT s.id FROM subjects s JOIN courses c ON c.id = s.course_id
    WHERE c.name = '2° Medio' AND s.name = 'Historia')
WHERE name = 'Trabajo Historia';

UPDATE evaluations SET subject_id = (
    SELECT s.id FROM subjects s JOIN courses c ON c.id = s.course_id
    WHERE c.name = '3° Medio' AND s.name = 'Matemáticas')
WHERE name = 'Prueba Funciones';

UPDATE evaluations SET subject_id = (
    SELECT s.id FROM subjects s JOIN courses c ON c.id = s.course_id
    WHERE c.name = '4° Medio' AND s.name = 'Matemáticas')
WHERE name = 'Prueba Integrales';
