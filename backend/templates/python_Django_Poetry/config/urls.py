from django.urls import path
from django.http import JsonResponse
def ping(request): return JsonResponse({"service":"{{SERVICE_NAME}}","status":"UP"})
urlpatterns = [path("api/v1/ping", ping)]
