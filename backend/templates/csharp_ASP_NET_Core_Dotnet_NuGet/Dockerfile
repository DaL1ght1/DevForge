FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY . .
RUN dotnet publish -c Release -o /out
FROM mcr.microsoft.com/dotnet/aspnet:8.0
COPY --from=build /out /app
ENTRYPOINT ["dotnet","{{SERVICE_NAME}}.dll"]
