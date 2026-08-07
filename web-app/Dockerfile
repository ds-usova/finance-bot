# Built from the repository root, the way every module in this repository is.
FROM node:22-alpine AS build
WORKDIR /repo/web-app
COPY web-app/package.json web-app/package-lock.json ./
RUN npm ci
COPY web-app/ ./
ARG VITE_TELEGRAM_BOT_USERNAME
ENV VITE_TELEGRAM_BOT_USERNAME=$VITE_TELEGRAM_BOT_USERNAME
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /repo/web-app/dist /usr/share/nginx/html
COPY web-app/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
