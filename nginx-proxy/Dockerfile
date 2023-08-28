# Use the official Nginx image as the base image
FROM nginx:latest

# Copy your custom Nginx configuration file into the container
COPY default.conf /etc/nginx/conf.d/
COPY includes/ssl.conf /etc/nginx/includes/ssl.conf
COPY ssl/my-webapp.crt /etc/nginx/certificate/my-webapp.crt
COPY ssl/my-webapp.key /etc/nginx/certificate/my-webapp.key

# Expose port 80
EXPOSE 80
EXPOSE 443

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
