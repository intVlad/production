# The compose file used to bind-mount this whole directory into nginx, which meant node_modules
# (59 MB of it) and package-lock.json were served to anyone who asked, alongside 272 KB of
# assets that are actually needed. Copying just the assets also makes the image self-contained,
# so a deployment does not depend on a checkout being present on the host.
FROM nginx:alpine

COPY *.html /usr/share/nginx/html/
COPY *.css  /usr/share/nginx/html/
COPY js/    /usr/share/nginx/html/js/
COPY css/   /usr/share/nginx/html/css/

EXPOSE 80
