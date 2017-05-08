package controllers

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws._
import scala.concurrent._
import ExecutionContext.Implicits.global
import javax.inject.{Inject, Singleton}
import scala.util.Properties
import scala.collection.mutable.ListBuffer
import models._

@Singleton
class WeatherController @Inject() (ws: WSClient, citiesController: CitiesAPIController) extends Controller {
    val API_KEY = Properties.envOrElse("WEATHER_API_KEY", "WEATHER_API_KEY")

    def getWeather(city: String): Future[WSResponse] = {
        val url = s"http://api.openweathermap.org/data/2.5/weather?q=${city}&appid=${API_KEY}"
        ws.url(url).get()
    }

    def getWeatherForCity(city: String) = Action.async {request =>
        val city = request.getQueryString("city").mkString
        val citiesFuture = citiesController.getCities(city);
        citiesFuture.flatMap {
            response => {
                val resp = Json.parse(response.body)
                val predictions = (resp \ "predictions")
                val jsr = predictions.validate[Seq[GoogleCity]]
                var futures: Seq[Future[WSResponse]] = Seq(ws.url("http://localhost").get())
                jsr.fold(
                    errors => {
                         Future(Ok("errors"))
                    },
                    cities => {
                        futures = cities.map { item =>
                            getWeather(item.description)
                        }
                })
                val f = Future sequence futures
                f map {
                    case results => {
                        var list = new ListBuffer[WeatherResponse]
                        results.map { result => {
                            val resp = Json.parse(result.body)
                            val jsresp = (resp).validate[WeatherResponse]
                            jsresp.fold (
                                err => println(err),
                                weath => list += weath
                            )
                        }
                        }
                        Ok(Json.arr(list.toList))
                    }
                    case t => BadRequest("An error has occured: " + t)
                }
            }
        }
    }
}
