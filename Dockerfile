# Use the official Nginx image as the base image
FROM nginx:latest

# Copy your custom Nginx configuration file into the container
COPY nginx-proxy/default.conf /etc/nginx/conf.d/
COPY nginx-proxy/includes/ssl.conf /etc/nginx/includes/ssl.conf
COPY nginx-proxy/ssl/my-webapp.crt /etc/nginx/certificate/my-webapp.crt
COPY nginx-proxy/ssl/my-webapp.key /etc/nginx/certificate/my-webapp.key
COPY public /www/data

# Expose port 80
EXPOSE 80
EXPOSE 443

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
