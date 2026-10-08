FROM node:20-alpine AS build
RUN npm install -g pnpm@9
WORKDIR /app
COPY package.json pnpm-lock.yaml* ./
RUN if [ -f pnpm-lock.yaml ]; then pnpm install --frozen-lockfile; else pnpm install --no-frozen-lockfile; fi
COPY . .
RUN pnpm build

FROM node:20-alpine
RUN npm install -g pnpm@9
WORKDIR /app
ENV NODE_ENV=production
COPY package.json pnpm-lock.yaml* ./
RUN if [ -f pnpm-lock.yaml ]; then pnpm install --prod --frozen-lockfile; else pnpm install --prod --no-frozen-lockfile; fi
COPY --from=build /app/dist ./dist
EXPOSE 8080
CMD ["node", "dist/main.js"]