package com.limhs.movie_project;
/*

* 좋은 객체지향설계의 5가지 원칙 (SOLID)
  1. SRP(Single Responsibility Principle): 단일 책임원칙
     - 하나의 클래스는 하나의 책임만 가져야 한다.
  2. OCP (Open-Closed Principle): 개방-폐쇄 원칙
     - 소프트웨 요소는 확장에 열려 있으나, 변경에는 닫혀 있어야 한다.
  3. LSP (Liskov Substitution Principle): 리스코프 치환 원칙
     - 상위타입 객체를 하위타입 객체 대체하여도 정상적으 동작해야 한다(subtype관계 성립)
  4. ISP (Interface Segregation Principle): 인터페이스 분리 원칙
     - 각 클라이언트가 필요로 하는 인터페이스를 분리함해서 클라이언트가 사용하지 않는 인터페이스에 변경이 발생해도 다른 인터페이스는 영향을 받지 않도록 만드는 것
  5. DIP (Dependency Inversion Principle): 의존 역전 원칙
     - 구체적인 구현 클래스에 의존하지 말고, 추상화된 인터페이스에 의존해야 한다.

* 스프링의 핵심은 Thread Pool + Annotation + Reflection이다.
* 톰캣 서버가 클라이언트 요청이 들어오면 Thread Pool에서 스레드를 꺼내서 이를 처리한다.
* 스프링은 개발자가 달아둔 여러 Annotation을 통해 생성된 클래스/메서드 메타데이터 정보를 리플렉션을 활용해서 읽고,
* 리플렉션을 활용해서 외부에서 객체를 생성하고 관리함으로서 DI와 IoC등 다양한 기능을 제공한다.
* 스프링에서 제공하는 다양한 기능이 새로운 기술이 아니라, 에노테이션과 리플렉션을 극한으로 활용한 프레임워크일 뿐이다.
*
*
* Spring Core: 스프링은 객체 지향 설계를 용이하게 해주는 프레임워크이다.
*
* @Configuration을 통해서 여러 설정을 할 수 있다. -> 이 에노테이션을 붙이면 스프링이 부팅되는 시점에 해당 설정파일을 읽고 설정을 한다.
* 실제 스프링 컨테이너는 ApplicationContext이다. 이 ApplicationContext에서 스프링 빈들을 생성 및 관리하며, bean을 꺼낼 수 있다.
* 최상위 인터페이스: Bean Factory, Bean Factory의 구현 인터페이스: ApplicationContext
* 설정정보는 XML또는 annotation기반으로 생성할 수 있는데, 이는 각각의 구현체에서 설정정보를 읽어 BeanDefinition이라는 인터페이스 객체를 생성해주기 때문이며,
* 스프링 컨테이너는 이 BeanDefinition 정보를 읽어 스프링 빈을 생성한다.
*
* ApplicationContext applicationContext = new AnnotationConfigApplicationContext(AppConfig.class);
* 를 통해서 스프링 컨테이너를 직접 생성할 수 있다.
* 이때 ApplicationContext는 인터페이스이고, 구현체로 AnnotationConfigApplicationContext가 존재함.
* 이건 AppConfig라는 설정파일을 읽고, 스프링 컨테이너를 생성한 다음, 스프링 빈을 생성 후 스프링 컨테이너에 등록한다.
* 스프링 빈을 생성한 다음에는, 설정 정보를 읽어서 스프링 빈 간의 의존관계를 설정한다.
* 다만 스프링 컨테이너는 기본적으로 싱글톤 컨테이너이기 때문에, 스프링 빈으로 등록되는 객체는 state를 가지면 안된다. 즉 stateless해야 하며 불변이어야 한다.(여러곳에서 공유학 ㅣ때문에)
* 스프링 컨테이너 설정파일 AppConfig에서 @Configuration을 사용하면 AppConfig@CGLIB로 변환되면서 바이트코드를 조작하여 다음과 같은 작업을 수행한다.
* 의존관계를 분석해서, 특정 스프링 빈을 여러 곳에서 의존하는 경우, 미리 생성해둔 스프링 빈을 주입한다. 따라서 싱글톤을 보장한다.
* 만약 @Configuration을 설정하지 않고 @Bean만 사용한다면 이러한 작업이 수행되지 않아 싱글톤이 보장되지 않고, 여러 인스턴스가 따로 생성될 수 있다. -> 싱글톤이 깨지게 된다.
*
* 하지만 이렇게 설정파일에 일일히 의존관계와 스프링 빈을 등록하는 작업은 매우 번거로움 -> 컴포넌트 스캔 방식이 나타났다.
* 스프링은 설정 정보가 없어도 자동으로 에노테이션을 읽어 스프링 빈으로 등록해주는 컴포넌트 스캔을 제공한다.
* 또한 의존관계를 자동으로 주입해주는 @AutoWired도 제공한다.
*
* 컴포넌트 스캔은 설정파일에 @ComponentScan이라는 에노테이션을 붙임으로서 설정할 수 있다.
* 컴포넌트 스캔은 각 class들에 @Component라는 에노테이션을 붙이면, 스프링이 @Component 에노테이션이 붙은 클래스들의 인스턴스를 생성해서 스프링 빈으로 등록한다.
* 이때 의존관계 주입의 경우에는 생성자에 @AutoWired를 붙이고 인스턴스를 주입해주면 스프링이 의존관계도 주입해준다.
*
* 이때 Lombok을 사용해서 @RequiredArgsConstructor 를 사용하면 생성자를 자동으로 생성해주며, 이때 @Autowired를 붙여서 의존관계 주입도 설정해준다.
*
* 그리고 스프링 부트를 사용하면 @SpringBootApplication 에노테이션안에 @ComponentScan이 들어있기 때문에 컴포넌트 스캔을 자동으로 수행한다.
* 개발자는 각 Class에 @Component와 같은 에노테이션을 잘 붙이고, 생성자에 @Autowired를 설정하면 컴포넌트 스캔을 통해 스프링 빈 등록 + 의존관계 주입을 해준다.
*
* 컴포넌트 스캔의 대상이 되는 에노테이션 목록
* @Component
* @Controller
* @Service
* @Repository
* @Configuration
*
* 도메인의 경우에는 보통 @Entity를 통해 엔티티로 등록하는데, 도메인의 경우에는 일반적으로 state를 관리하는 경우가 많기 때문에 스프링 빈으로 등록하지 않는다.
*
* 수동 빈등록: 설정정보(AppConfig) 를 통해 직접 @Bean 메서드를 통해서 스프링 빈을 등록, 클래스에서는 외부에서 의존관계를 주입받기 때문에 실제 구현체를 직접 의존할 필요 x
* 자동 빈등록: 컴포넌트 스캔과 @Autowired를 통해서 자동 의존관계 주입 및 스프링 빈 등록
*
* 우선순위는 수동 빈 등록 > 자동 빈 등록
*
* 의존관계를 주입하는 방법은 총 4가지가 존재
* 1. 생성자 주입(가장 많이 사용) -> 생성자 호출 시점에 딱 한번만 호출. 이후 변하지 않음(불변, 필수 의존관계에 사용)
*    생성자가 하나일때에는 @Autowired를 생략해도 된다.(이를 통해 @RequiredArgsConstructor로 생성자를 생성시 의존관계가 주입된다)
*    하지만 스프링 컨테이너는 싱글톤 컨테이너이기 때문에 인스턴스가 stateless해야 하며 불변을 권장하기에 오히려 생성자 주입과 잘 맞는다
* 2. 수정자 주입(setter 주입) -> setter 메서드에 @Autowired를 설정하는 방법. 선택 / 변경의 가능성이 있는 의존관계에 사용
* 3. 필드 주입 -> 멤버 변수 선언과 동시에 의존관계를 주입해주는 방식. 하지만 이 방식은 Spring이 없으면 동작자체가 안되기 때문에 권장하지 않는다
* 4. 일반 메서드 주입 -> 한번에 여러 의존관계를 주입할 수 있다. 하지만 이도 잘 사용하지 않는다.
*
* Lombok: 생성자, Setter, Getter등을 자동으로 만들어주는 라이브러리
* @RequiredArgsConstructor를 사용하면 final이 붙은 필드를 자동으로 모아서 생성자를 만들어준다.
* 이때 생성자가 하나라면 @Autowired가 자동으로 붙기 때문에 자동 의존관계 주입이 된다.
* 다만 유의할 점은 @RequiredArgsConstructor는 final이 붙은 필드만 모아서 생성자를 만들기 때문에 이 점을 유의해야 한다.
*
* 또한 Autowired는 타입을 기반으로 조회하기 때문에, 만약 특정 인터페이스를 구현한 구현체가 여러개고, 구현체들이 모두 스프링 빈으로 등록된다면
* Autowired는 어떤 구현체를 주입해줘야 할지 모르기 때문에 오류가 발생한다. 따라서 사용할 구현체만 스프링 빈으로 등록해야 한다.
* 또는 @Primary 에노테이션을 통해서 특정 구현체에게 우선순위를 부여할 수도 있다.
*
* ApplicationContext는 기본적으로 spring boot가 생성하여 스프링 내부에서 돌아가고 있고, 개발자가 ApplicationContext인스턴스를 추가로 생성해서 운용할 수도 있다.
*
* (빈 생명주기 콜백)
* 스프링 컨테이너 생성 -> 스프링 빈 생성 -> 의존관계 주입 -> 초기화 콜백 -> 사용 -> 소멸전 콜백 -> 스프링 종료
* 스프링은 의존관계 주입이 완료되면 스프링 빈에게 콜백 메서드를 통해서 초기화 시점을 알려주는 다양한 기능을 제공한다.
* 또한 스프링은 스프링 컨테이너가 종료되기 직전에 스프링 빈에게 콜백 메서드를 통해서 종료 작업을 진행할 수 있도록 한다.
*
* 초기화 콜백 / 소멸전 콜백을 구현하는 방식에는
* 1. 인터페이스(InitializingBean, DisposableBean) 구현 -> afterPropertiesSet() 과 destroy() 메서드 오버라이딩
* 2. 설정 정보에 초기화 메서드, 종료 메서드 지정 -> @Bean(initMethod = "init", destroyMethod = "close")
* 3. @PostConstruct, @PreDestroy 에노테이션 설정 -> 매우 편리하기 때문에 주로 사용된다. 이는 스프링에 종속적인 기능이 아니라 자바에서 지원하는 기능이다.
* 이 있다.
*
* (빈 스코프)
* 스프링은 다음과 같은 다양한 스코프를 지원한다.
* 싱글톤: 기본 스코프, 스프링 컨테이너의 시작과 종료까지 유지되는 가장 넓은 범위의 스코프. -> 기본적으로는 싱글톤 빈을 사용하면 된다.
* 프로토타입: 스프링 컨테이너는 프로토타입 빈의 생성과 의존관계 주입까지만 관여하고 더는 관리하지 않는 매우 짧은 범위의 스코프 -> 빈을 생성하고 관리하지 않기 때문에 매번 새로운 인스턴스가 생성된다. 프로토타입의 경우 빈 생성 이후 빈에 대한 책임은 클라이언트에게 있기 때문에 @PreDestroy등도 호출되지 않는다.
* 웹 관련 스코프:
*   request: 웹 요청이 들어오고 나갈때 까지 유지되는 스코프이다.
*   session: 웹 세션이 생성되고 종료될 때 까지 유지되는 스코프이다.
*   application: 웹의 서블릿 컨텍스트와 같은 범위로 유지되는 스코프이다. (ServletContext)
*   websocket: 웹 소켓과 동일한 생명주기를 가지는 스코프
*
* 스프링 빈의 스코프는 @Scope(prototype)처럼 에노테이션으로 설정할 수 있다.
*
* 싱글톤 빈에서 프로토타입 빈을 의존관계 주입을 받게 되면, 프로토타입 빈이라고 해도, 해당 싱글톤 빈 안에서 함께 유지되기 때문에 사실상 싱글톤처럼 동작하게 된다.
*
* Provider: 필요한 시점에 Bean을 직접 꺼내오기 위한 객체
*
* 기본적으로 DI는 애플리케이션 시작 시점에 의존관계가 미리 주입된다.
* 하지만 어떤 경우에는 지금 당장 필요할 때마다 새로 Bean을 가져오고 싶은 경우가 존재할 수 있다.(singleton bean 안에서 prototype/request bean 사용)
* 만약 싱글톤 빈에서 시작시점에 프로토타입 빈을 주입받게 되면, 프로토타입 빈도 싱글톤 빈과 함께 유지되어 매번 새롭게 생성되지 않는다.
* 반면 매번 필요할때마다 새로 Bean을 주입받는 방법을 사용하면 싱글톤 내에서도 프로토타입 빈을 매번 새롭게 주입받을 수 있다.
*
* 이럴때에는 Provider를 등록해두고, 필요한 시점에 provider.get을 통해 그때그때 새로 Bean을 가져올 수 있다. (lazy lookup)
*
* 또한 @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS) 와 같이 Proxy를 설정하게 되면 HTTP request와 상관 없이 가짜 프록시 클래스를
다른 빈에 미리 주입해 둘 수 있다.
* 이후 실제 http request가 들어오면 실제 request 빈을 생성한 다음, 프록시 빈을 실제 request 빈으로 위임한다.
*
* (웹 서버 설정)
* spring-boot-starter-web 라이브러리를 추가하면 스프링 부트는 내장 톰캣 서버를 활용하여 웹 서버와 스프링을 함께 실행시킨다
* 스프링부트는 웹 라이브러리가 없다면 AnnotationConfigApplicationContext 을 기반으로 애플리케이션을 구동한다.
* 웹 라이브러리가 있다면 AnnotationConfigServletWebServerApplicationContext 를 기반으로 애플리케이션을 구동한다.
* */

/*
 * Spring MVC: 스프링에서 웹 요청을 처리하고 관리하는 기능을 제공하는 프레임워크
 *
 * Tomcat: 웹 서버 + Servlet Container (1. socket 열기 2. HTTP 요청 받기 3. Servlet 실행)
 * Tomcat이 Thread pool을 관리하며, HTTP 요청이 들어올때마다 Thread pool에서 Thread 하나를 꺼내서 이를 처리한다.
 * Servlet: 자바에서 HTTP 요청을 처리하기 위한 객체 (Http Request를 파싱하고, response를 내보내는 역할)
 *
 * Tomcat이 HTTP 요청을 파싱해 HTTPServletRequest, HTTPServletResponse를 생성 후, 객체 Servlet에 넘긴다.
 * Servlet에서는 이 정보들을 바탕으로 적절한 Controller를 호출하고, 응답 데이터 HTTPServletResponse에 담는다.
 * 이후 Tomcat이 HTTPServletResponse정보를 바탕으로 HTTP 응답메세지를 생성후 클라이언트에게 전
 *
 * HTTP 요청메세지로 데이터를 전달하는 방법
 * 1. HTTP GET 메세지에 쿼리 파라미터로 데이터를 전달하는 방법 (/url**?username=hello&age=20**)
 * 2. HTTP POST 메세지 바디에 쿼리 파라미터 형식으로 데이터를 전달하는 방법
 * 3. HTTP 메세지 바디에 직접 데이터를 전달하는 경우(JSON 형식)
 *
 * JSP: HTML안에 자바 코드를 넣는 기술(옜날 기술)
 * MVC: Model + View + Controller (역할 분리 아키텍처 패턴)
 *
 * 스프링부트에서는 서블릿을 직접 등록해서 사용할 수 있는 @ServletComponentScan을 지원한다.(@WebServlet과 같은 에노테이션을 읽어 서블릿으로 등록)
 * 스프링은 기본적으로 디스패처 서블릿을 사용하는데 @ServletComponentScan을 사용하면 개발자가 추가적인 서블릿을 등록할 수 있다.
 *
 * 내장 톰켓 서버에는 웹 에플리케이션 서버와 서블릿 컨테이너가 존재
 * 웹 에플리케이션 서버: 클라이언트로부터 Request를 받고, Response를 내보내는 역할
 * 서블릿 컨테이너: 웹 에플리케이션 서버로부터 입력받은 Request를 파싱하여 분석하고, 적절한 서비스를 호출하고, 결과를 Response format를 생성해서 웹 에플리케이션 서버에 전달
 * 스프링에는 기본적으로 서블릿 컨테이너에 디스패처 서블릿이 존재함
 * "/hello"   → HelloServlet
 * "/members" → MemberServlet
 * "/api/*"   → ApiServlet
 * 서블릿 컨테이너는 다음과 같이 매핑테이블을 관리한다.
 *
 * HttpServletRequest / HttpServletResponse: 스프링의 서블릿 컨테이너(Tomcat)에서 자동으로 생성해서 서블릿에게 전달한다
 * 서블릿에서는 HTTPRequest를 자동으로 파싱하여 HttpServletRequest라는 객체에 담아서 리턴해준다.
 * 이 HttpServletRequest 객체 정보를 읽어서 HTTP 요청 정보를 얻을 수 있다.
 *
 * (JSP)
 * 자바 코드로 HTTP Format을 맞추기위해 HTML 코드를 작성하는 것은 매우 번거로움
 * HTML 코드에서 특정 부분만 자바 코드를 사용하는것이 효츌적이다 -> 탬플릿 엔진(JSP, Thymeleaf ...)
 *
 * JSP를 통해서 HTML(view)에서 비즈니스 로직까지 다 처리해버리면 클라이언트에게 의도하지 않는 정보까지 노출되게 된다. 따라서 비즈니스 로직과 view를 분리해야 한다. -> MVC 패턴
 *
 * (MVC 패턴)
 * 컨트롤러: HTTP 요청을 받아서 파라미터를 검증하고, 비즈니스 로직을 실행한다. 그리고 뷰에 전달할 결과 데이터를 조회해서 모델에 담는다.
 * 모델: 뷰에 출력할 데이터를 담아둔다. 뷰가 필요한 데이터를 모두 모델에 담아서 전달해주는 덕분에 뷰는 비즈니스 로직이나 데이터 접근을 몰라도 되고, 화면을 렌더링 하는 일에 집중할 수 있다.
 * 뷰: 모델에 담겨있는 데이터를 사용해서 화면을 그리는 일에 집중한다. 여기서는 HTML을 생성하는 부분을 말한다. -> 컨트롤러에는 의존하지 않고 모델에만 의존하면 된다.
 *
 * Controller (비즈니스 로직 호출(Service) + 결과를 Model에 담기) -> Model(결과 저장) -> View(Model 정보를 기반으로 화면에 출력)
 *
 * 이때 Controller에서 비즈니스 로직을 직접 실행하는 것이 아니라, Service계층에 구현되어 있는 비즈니스 로직을 호출한다.
 *
 * 과거에는 Model은 HttpServletRequest 객체를 사용한다. request는 내부에 데이터 저장소를 가지고 있는데, request.setAttribute(), request.getAttribute()를 사용하면 데이터를 보관하고, 조회할 수 있다.
 * 그런데 SpringMVC에서는 Model이라는 추상화된 객체를 제공한다.
 *
 * (Front Controller)
 * 스프링의 DispatcherServlet은 FrontController역할을 수행한다.
 * DispatcherServlet이 모든 요청을 입력받고, 이를 분석해서 적절한 Controller를 호출하는 역할을 한다
 *
 * HandlerAdapter는 “다양한 방식의 Handler(Controller)를 DispatcherServlet이 동일한 방식으로 호출할 수 있게 해주는 어댑터”
 * HandlerAdapter를 활용하면 개발자가 통일된 방식이 아니라, 매번 다른 형태의 파라미터, 리턴값을 전달하더라도, 어뎁터가 이를 변형하여 적절한 형태로 내부적으로 수정해준다.
 * 따라서 개발자는 항상 일관된 방식이 아니라, 그때그때 원하는 방식으로 Controller를 호출할 수 있다.
 *
 * 개발자가 컨트롤러를 구현할 때 서로다른 파라미터, 서로다른 리턴값들을 선언하여 구현하더라도, 핸들러어뎁터가 이들을 동일한 인터페이스로 맞춰준다.
 *
 * SpringMVC 동작 과정
 * 1. HTTP 요청
 * 2. Dispatcher Servlet이 HandlerMapping을 조회하여 Handler를 조회 (Handler란 HTTP 요청을 처리할 수 있는 객체를 의미하고 일반적으로 Controller를 의미한다.)
 * 3. Handler Adaptor 목록을 기반으로 해당 Handler를 처리할 수 있는 Handler Adaptor를 조회
 * 4. 핸들러 어뎁터를 호출 -> 핸들러 어뎁터에서 공통된 인터페이스로 형태를 변환
 * 5. 핸들러 어뎁터는 ArgumentResolver를 호출하여 메서드에 필요한 파라미터 값을 읽고 채워준다.
 * 5-1. 이때 Argument 중에 @RequestBody나 @HTTPEntity를 사용한 코드가 있으면 HttpMessageConvertor를 호출하여 메세지 본문 -> 객체로 변환하여 파라미터를 채워준다.
 * 6. 핸들러 어뎁터가 핸들러(컨트롤러)를 호출
 * 7. 핸들러(컨트롤러)가 결과를 핸들러 어뎁터에게 반환
 * 7-1. 이때 메서드에서 @Responsebody나 @HTTPEntity를 사용한 경우에는 ViewResolver를 호출하는 것이 아니라, HttpMessageConvertor를 호출하여 객체 -> 메세지 본문으로 변환하여 HTTP 응답 본문에 직접 값을 채워준다.
 * 8. 핸들러 어뎁터는 결과값을 받아서 ModelAndView 형태로 변환해서 DispatcherServlet에게 전달(ModelAndView에는 View에 대한 논리이름, View에 전달할 Model값이 담겨있다.)
 * 9. Dispatcher Servlet은 ViewResolver를 호출하여 View에 대한 논리 이름을 기반으로 실제 View를 찾아서 return한다.
 * 10. Dispatcher Servlet은 리턴받은 View를 기반으로 render(Model)을 통해 해당 View에 Model 정보를 전달한다.
 * 11. View가 Model정보를 기반으로 HTML을 렌더링해서 HTML response를 생성한다.
 *
 *  여기서 View는 단순히 HTML 코드 파일이 아니라 객체이다.
 * public interface View {

    void render(
        Map<String, ?> model,
        HttpServletRequest request,
        HttpServletResponse response
    );
}
 *
 * 디스패처 서블릿에서는 JSP를 쓰든 Thymeleaf를 쓰든 상관없이, return받은 View 객체를 활용해서 View.render()를 호출한다.
 * JSP를 사용하는 경우에는 View의 구현체로 InternalResourceView를 사용하고, Thymeleaf의 경우에는 ThymeleafView 객체를 사용한다.
 * 따라서 HTML template 자체가 View인것이 아니라, View 객체가 해당 template에 Model 정보를 활용해서 데이터를 채워서 HTTP Response를 생성한다.
 *
 * 그러면 개발자가 너무 이상한 format의 Controller(Handler)를 구현하는것만 아니라면,
 * 이미 스프링에서 적절한 Handler Adaptor를 다 구축해놨기 때문에, 내부적으로 공통적인 Format으로 변환해서 정상적으로 동작한다.
 * 즉 개발자가 Controller(Handler)의 Format을 맞추려고 너무 애쓸 필요가 없다.
 *
 * 하나의 컨트롤러 내에는 다양한 매핑 메서드가 존재할 수 있음. 그리고 다양한 매핑 메서드는 서로 다른, 다양한 파라미터를 받을 수 있다.
 * 따라서 HandlerMapping을 조회하여 Handler를 조회하는것은 특정 메서드가 포함된 Controller를 찾는 행위이며(Request를 처리할 수 있는 Controller),
 * 그리고 서로 다른, 다양한 파라미터를 받는 매핑 메서드는 핸들러 어뎁터를 통해서 공통된 Interface로 변환된다. (ArgumentResolver를 통해서 여러 파라미터를 받을 수 있다.)
 *
 * Handler Mapping에는 특정 url과 이 url을 처리할수 있는 Controller + Controller 내의 메서드를 매핑해서 저장하고 있다.
 * Handler Mapping에서는 특정 url을 처리할 수 있는 Controller와 메서드 정보가 담겨있는 HandlerMethod를 리턴한다.("어떤 Controller의 어떤 메서드를 실행할지")
 * Handler Adaptor는 HandlerMethod 정보를 읽어서, 해당 메서드를 처리하고, 변환할 수 있는 어뎁터를 찾는다.(파라미터 분석, ArgumentResolvor 선택, 파라미터 생성, 리턴값 변환)
 * 즉 컨트롤러의 메서드에서 사용하는 파라미터들은 Handler Adaptor에서 호출하는 Argumentresolver에서 생성해서 주입해준다.
 *
 * Handler Mapping의 예시:
 * 0 = RequestMappingHandlerMapping : 애노테이션 기반의 컨트롤러인 @RequestMapping에서 사용
 * 1 = BeanNameUrlHandlerMapping : 스프링 빈의 이름으로 핸들러를 찾는다.
 *
 * Handler Adapter의 예시:
 * 0 = RequestMappingHandlerAdapter : 애노테이션 기반의 컨트롤러인 @RequestMapping에서 사용
 * 1 = HttpRequestHandlerAdapter : HttpRequestHandler 처리
 * 2 = SimpleControllerHandlerAdapter : Controller 인터페이스(애노테이션X, 과거에 사용) 처리
 *
 * View Resolver의 예시:
 * 1 = BeanNameViewResolver : 빈 이름으로 뷰를 찾아서 반환한다. (예: 엑셀 파일 생성 기능에 사용)
 * 2 = InternalResourceViewResolver : JSP를 처리할 수 있는 뷰를 반환한다.
 * 3 = ThymeleafViewResolver : Thymeleaf를 처리할 수 있는 뷰를 반환한다.
 *
 * @Controller가 붙은 클래스를 Spring이 컴포넌트 스캔으로 Bean 등록하고,
 * 그 안의 @RequestMapping, @GetMapping, @PostMapping 같은 메서드들을 분석해서 HandlerMapping에 매핑 정보를 등록한다.
 *
 * 요즘의 스프링에서의 컨트롤러는 대부분 @RequestMapping이라는 에노테이션을 사용한다.
 * @RequestMapping을 사용하면 하나의 컨트롤러 내부에서 여러 url을 처리할 수 있다.
 * RequestMappingHandlerMapping 은 스프링 빈 중에서 @RequestMapping 또는 @Controller 가 클래스 레벨에 붙어 있는 경우에 매핑 정보로 인식한다
 *
 * RequestMapping은 GetMapping이나 PostMapping처럼 HTTP 요청 메서드에 따라서 나눌 수 있다.
 *
 * 또한 메서드의 파라미터에 @RequestParam을 사용하면 ArgumentResolver가 HttpServletRequest에서 해당 파라미터정보를 꺼내서 메서드 파라미터에 넣어준다.
 *
 * (Spring Logging)
 * 스프링에서는 SLF4J 라이브러리를 통해서 로깅 기능을 제공한다.
 * SLF4J는 인터페이스이고, 그 구현체로 Logback 같은 로그 라이브러리를 선택하면 된다.
 * private Logger log = LoggerFactory.getLogger(getClass());
 * private static final Logger log = LoggerFactory.getLogger(Xxx.class)
 *
 * @Controller는  메서드의 반환 값이 String이면, view name으로 인식된다.
 * 반면 @RestController는 메서드의 반환값이 String이면, 해당 문자열을 HTTP 메세지 바디에 바로 입력한다.
 * 또한 메서드에 @ResponseBody를 붙이면 View 객체를 조회하는 것이 아니라, 실제 HTTP message body에 정보를 바로 입력한다.
 *
 * 그리고 Controller는 메서드에서 경로변수 @PathVariable을 사용할 수 있다.
 * @PathVariable이란 Url 경로에서 값을 꺼내오는 역할을 한다.
 *
 * @GetMapping("/mapping/{userId}")
 * public String mappingPath(@PathVariable("userId") String data) {
 *      log.info("mappingPath userId={}", data);
 *      return "ok";
 * }
 *
 * @RequestParam과 @PathVariable의 차이점:
 * @RequestParam은 HTTPServletRequest에서 파라미터 정보를 꺼내서 메서드의 파라미터로 전달해준다.
 * @PathVariable은 url 경로에 있는 경로 변수 값을 메서드의 파라미터 정보로 전달해준다.
 *
 * 그리고 Controller에 RequestMapping()에 base URL을 설정할 수있다. 그러면 메서드에 작성하는 url은 상대주소가 된다.
 *
 * @RequestParam에서 required를 통해 파라미터 필수 여부를 결정할 수 있다.
 * 또한 기본형이거나 파라미터 명과 request parameter명이 같은 경우 @RequestParam을 생략해도 된다. 이 경우 required는 false로 설정된다.
 * 그리고 @RequestParam으로 특정 파라미터만 전달받을 수도 있지만,  MultiValueMap를 사용하면 모든 파라미터 정보를 얻을 수 있다.
 *
 * 실제 개발을 하면 요청 파라미터를 받아서 필요한 객체를 만들고 그 객체에 값을 넣어주어야 하는 경우가 발생한다. 이때 매번 객체를 생성하고 파라미터값을 넣어주는 것은 번거로움
 * 스프링에서는 @ModelAttribute를 통해 이 과정을 자동화해준다.
 * @ResponseBody
   @RequestMapping("/model-attribute-v1")
   public String modelAttributeV1(@ModelAttribute HelloData helloData) {
        log.info("username={}, age={}", helloData.getUsername(),
        helloData.getAge());
        return "ok";
   }
 * 다음과 같이 @ModelAttribute를 사용하면 해당 객체를 생성하고, 해당 객체의 프로퍼티를 찾고, 해당 프로퍼티의 setter를 호출하여 파라미터의 값을 바인딩한다.
 * 이렇게 객체를 생성하고 파라미터를 바인딩하는 작업은 ArgumentResolver가 @ModelAttribute를 읽고 수행한다.
 * 이러한 @ModelAttribute도 생략할 수 있다.
 *
 * Controller의 메서드들에는 @RequestParam도 생략할 수 있고, @ModelAttribute도 생략할 수 있다.
 * 이때 파라미터 타입이 기본형이라면 @RequestParam이고, 참조형(객체)라면 @ModelAttribute로 인식된다.
 *
 * 또한 Controller의 메서드에서 요청메세지의 헤더정보 뿐만 아니라 메세지 바디 부분의 정보를 얻기 위해서는 다음과 같은 방법을 사용한다.
 * InputStream / OutputStream: HTTP 요청 메세지 바디 내용을 직접 조회 / HTTP 응답 메세지 바디에 직접 결과 출력
 * HttpEntity: HTTP header, body 정보를 편리하게 조회
 * @RequestBody: HTTP 요청 메세지 바디 내용을 직접 조회할 수 있다. 만약 헤더 정보가 필요하면 @RequestHeader를 사용한다.
 *
 * @ResponseBody를 사용하여 View 객체를 호출하는 것이 아니라, 직접 HTTP 응답 메세지 바디에 직접 결과를 출력하는 경우에는 ViewResolver가 아니라 HttpMessageConverter가 호출된다.
 * 기본 문자처리: StringHttpMessageConverter / 기본 객체처리: MappingJackson2HttpMessageConverter
 *
 * 스프링 MVC는 다음과 같은 경우에 HTTP 메세지 컨버터를 적용한다.
 * HTTP 요청: @RequestBody, HttpEntity(RequestEntity)
 * HTTP 응답: @ResponseBody, HttpEntity(ResponseEntity)
 *
 * HTTP 요청이 들어오면 HTTPMessageConverter는 canRead()를 호출하여 메세지를 읽을 수 있는지 확인한다.
 * CanRead() 조건을 만족하면 read()를 호출하여 객체를 생성하고 반환한다.
 *
 * HTTP 응답시에는 HTTPMessageConverter가 CanWrite()를 호출하여 메세지를 쓸 수 있는지 확인한다.
 * canWrite() 조건을 만족하면 write()를 호출하여 HTTP 응답 메세지 바디에 데이터를 생성한다.
 *
 * 보통 API 통신은 HTTP 메세지 Body에 Json 데이터를 주고 받는 경우가 많은데, HTTPMessageConverter는 Json 데이터 -> 자바 객체 / 자바 객체 -> Json 데이터로 변환해주는 역할을 한다.
 *
 * (Redirect)
 * 컨테이너의 메서드에서는 뷰를 호출하는 것이 아니라 리다이렉트를 호출할 수도 있다.
 * return "redirect:/basic/items"; 이런식으로 return하게 되면 redirect 할 수 있다. -> 이때 클라이언트는 해당 Url로 Get 요청을 보내게 된다.
 *
 * Redirection의 주의점: PRG Post/Redirect/Get
 * 웹브라우저의 새로고침은 마지막에 서버에 전송한 데이터를 다시 전송한다.
 * 상품 등록 폼에서 데이터를 입력하고 저장을 선택하면 POST /add + 상품 데이터를 서버로 전송한다.
 * 상태에서 새로 고침을 또 선택하면 마지막에 전송한 POST /add + 상품 데이터를 서버로 다시 전송하게 된다. -> 상품이 누적되는 문제가 발생한다.
 *
 * 이때 Post -> (Redirect) -> Get을 통해 마지막 요청 정보를 Post가 아니라 Get으로 유지시키면 이 문제를 해결할 수 있다.
 * 또한 RedirectAttributes 객체의 addAttribute를 사용하면 Redirect시의 쿼리 파라미터를 설정할 수가 있다.
 *
 * Redirect와 Forwarding의 차이:
 * Redirect는 클라이언트가 서버로 해당 url과 쿼리 파라미터로 다시 요청을 보내는것.
 * Forwarding은 서버 내부에서 어떤 view를 호출할지를 결정하는것(클라이언트의 요청은 한번)
 *
 * (Thymeleaf)
 * Thymeleaf의 3가지 특징:
 * 서버 사이드 HTML 렌더링(SSR): 타임리프는 백엔드 서버에서 HTML을 동적으로 렌더링 하는 용도로 사용된다
 * 네츄럴 템플릿: 타임리프는 뷰 탬플릿을 거치지 않고 바로 열어봐도 잘 렌더링된다.
 * 스프링 통합 지원
 *
 * (메세지 국제화)
 * 스프링부트는 부팅시에 MessageSource 객체를 스프링 빈으로 등록한다.
 * 이후 messages.properties나 messages.en_properties등에 메세지들을 작성해놓고, MessageSource를 활용하면 메세지 국제화가 가능하다.
 *
 * (Validation)
 * 스프링에서는 클라이언트 요청이 적절한지 검증하기 위한 Validation이 필요하다(Validation)
 * 스프링에서는 검증 오류를 처리하기 위해 BindingResult라는 객체를 제공한다.
 * @PostMapping("/add")
   public String addItemV1(@ModelAttribute Item item, BindingResult bindingResult, RedirectAttributes redirectAttributes)
 *
 * 이때 BindingResult는 검증하기 위한 객체 바로 뒤에 나와야한다.
 * BindingResult는 특정 필드에서 발생한 오류를 저장할 수 있는 FieldError와, 특정 필드를 넘어서는 전역적인 오류를 저장할 수 있는 ObjectError를 제공한다.
 * 하지만 매번 FieldError와 ObjectError 객체르 생성해서 BindingResult에 넣어주는 것은 번거롭기 때문에 RejectValue와 Reject를 통해서 간단하게 넣을 수도 있다.
 * 하지만 이렇게 Validation하는 과정 자체가 너무 복잡하고 어려움 -> Spring에서는 Bean Validation이라는 편리한 기능을 제공
 *
 * (Bean Validation)
 * Bean Validation이란 도메인 객체의 각 필드에 @NotBlank나 @NotNull과 같이 검증용 에노테이션을 설정하는 것이다.
 * public String addItem(@Validated @ModelAttribute Item item, BindingResult bindingResult, RedirectAttributes redirectAttributes)
 *
 * Bean Validation은 Controller의 메서드에서 다음과 같이 사용할 수 있으며, 검증할 객체에 @Validated를 설정하면 Bean Validator가 @NotNull과 같은 에노테이션을 보고 검증을 수행한다.
 * 만약 검증시에 오류가 발생하면 bindingResult에 FieldError와 ObjectError를 추가하고, BindingResult는 자동으로 Model에 추가된다.
 *
 * (로그인 처리와 쿠키, 세션)
 * 로그인을 유지하기 위해서는 서버에서 해당 사용자에 대한 쿠키를 생성해서 클라이언트에게 전달하고, 클라이언트는 매요청마다 쿠키를 함께 전달한다.
 * 쿠키에는 두가지 종류가 있다.
 * 영속 쿠키: 만료 날짜를 입력하면 해당 날짜까지 유지
 * 세션 쿠키: 만료 날짜를 생략하면 브라우저 종료시 까지만 유지
 *
 * Cookie idCookie = new Cookie("memberId", String.valueOf(loginMember.getId()));
 * response.addCookie(idCookie);
 *
 * 쿠키는 다음과 같이 Cookie 객체를 생성해서 response에 담아줌으로서 생성할 수 있다.
 * @GetMapping("/")
 * public String homeLogin( @CookieValue(name = "memberId", required = false) Long memberId, Model model)
 *
 * 클라이언트가 함께 전송한 쿠키 정보는 @CookieValue를 통해서 쉽게 확인할 수 있다.
 * 로그아웃의 경우에는 클라이언트에게 새로운 쿠키(만료 쿠키)를 생성해서 전달하면, 클라이언트의 쿠키가 만료되어 로그아웃할 수 있다.
 *
 * ***쿠키의 매우 중요한 문제점***
 * 쿠키는 클라이언트측에서 관리되기 때문에 클라이언트가 쿠키값을 임의로 조작할 수있다 -> 다른사람의 계정으로 로그인할 수도 있다.
 * 쿠키는 외부에 노출되기 때문에 해커가 쿠키정보를 통해 개인정보를 뽑을 수 있다 -> 보안에 매우 취약하다.
 *
 * 이를 해결하기 위해서는 쿠키에 중요한 정보를 담는 것이 아니라, 쿠키에는 예상 불가능한 임의의 값(해시값)을 넣고, 서버 측에서 해당 값과 사용자 정보를 매핑해서 보관하고 있어야 한다.
 *
 * 세션 동작 방식:
 * 쿠키는 클라이언트 측에서 관리하며 외부에 노출되기 때문에 보안상 매우 취약하다. 따라서 중요한 정보는 모두 서버에서 관리하고, 쿠키는 임의의 예측 불가능한 값으로 설정해야 한다.
 * 1. 클라이언트가 로그인하게 되면 서버측에서는 세션 저장소에 예측불가능한 임의의 값(UUID)와 사용자정보를 매핑해서 보관한다.
 * 2. 이후 서버는 클라이언트에게 mySessionId라는 이름으로 쿠키에 세션 ID 정보만 담아서 전달한다.
 * 3. 이후 클라이언트는 해당 mySessionId라는 쿠키를 통해서 서버에 로그인할 수 있다.
 *
 * 이렇게 중요한 정보는 서버에서 관리하고, 쿠키값은 예측 불가능한 값으로 설정하게 되면 쿠키에 중요한 정보가 담기지 않아 개인정보나 중요한 정보가 노출될 가능성이 줄어든다.
 * 하지만 만약 해커가 쿠키를 탈취하여, 새션ID 정보를 읽어서 해당 세션Id로 로그인할 수 있기 때문에  해커가 토큰을 털어가도 시간이 지나면 사용할 수 없도록 서버에서 세션의 만료시간을 짧게(예: 30분) 유지한다.
 *
 * 서블릿에서는 세션을 위해서 HttpSession이라는 기능을 제공한다.
 *
 * //세션이 있으면 있는 세션 반환, 없으면 신규 세션 생성
 * HttpSession session = request.getSession();
 * //세션에 로그인 회원 정보 보관
 * session.setAttribute(SessionConst.LOGIN_MEMBER, loginMember);
 *
 * Servlet Container(Tomcat)은 기본 세션 저장소(HttpSession 저장소)를 내부적으로 가지고 있다.
 * 이렇게 HttpSession을 활용해서 Session을 생성하고, JSession이라는 안전한 쿠키를 생성해서 클라이언트에게 전달할 수 있다.
 * 사용자 정보등과 같은 중요한 정보는 서버의 Session 저장소에서 관리된다.
 * 하지만 결국 쿠키로 전달되는 SessionID도 해커에게 노출되면, 해커가 해당 SessionID를 통해서 로그인할 수 있기 떄문에 아주 안전하지는 않다.
 *
 * (서블릿 필터)
 * 서블릿 필터는 로그인 한 사용자 / 로그인 하지 않은 사용자들을 필터링 하는 등 Controller 진입전 필터링하는 용도로 사용된다.
 * 스프링 시큐리티가 서블릿 필터의 체인으로 구성되어 있다.
 * HTTP 요청 -> WAS -> 필터 -> 서블릿 -> 컨트롤러
 *
 * 필터를 활용하면 다음과 같이 사용자를 필터링할 수 있다.
 * HTTP 요청 -> WAS -> 필터 -> 서블릿 -> 컨트롤러 //로그인 사용자
 * HTTP 요청 -> WAS -> 필터(적절하지 않은 요청이라 판단, 서블릿 호출X) //비 로그인 사용자
 *
 * 또한 필터는 여러개를 설정하여 chaining을 통해 연속적으로 호출할 수도 있다.
 * HTTP 요청 -> WAS -> 필터1 -> 필터2 -> 필터3 -> 서블릿 -> 컨트롤러
 *
 * public interface Filter {
        public default void init(FilterConfig filterConfig) throws ServletException {}
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException;
        public default void destroy() {}
   }
 *
 * 스프링 Filter는 다음과 같은 Interface로 제공되며, 해당 인터페이스를 구현하고 등록하면, 서블릿 컨테이너가 필터를 싱글톤 객체로 생성하고 관리한다.
 * init(): 필터 초기화 메서드, 서블릿 컨테이너가 생성될 때 호출된다
 * doFilter(): 고객의 요청이 올 때 마다 해당 메서드가 호출된다. 필터의 로직을 구현하면 된다.
 * destroy(): 필터 종료 메서드, 서블릿 컨테이너가 종료될 때 호출된다.
 *
 * 그리고 doFilter() 내부에서는 마지막에 항상
 * chain.doFilter(request, response);
 * 이걸 호출해줘야 한다.
 *
 * chain.doFilter는 다음 필터가 있으면 호출하고, 필터가 없으면 서블릿을 호출한다. 만약 이 로직이 없다면 다음 단계로 진행되지 않는다.
 *
 * 또한 Filter는 Config파일에서 FilterRegistrationBean을 사용하여 스프링 빈으로 등록하고 Filter의 Order, 순서, 체인, Filter가 적용될 url등을 설정해야 한다.
 *
 * 만약 로그인 인증을 하기 위해서는 HttpSession을 활용하여 getSession을 했을때 null이라면, 생성된 세션이 없다는 의미이므로 필터링하면 된다.
 *
 * 스프링 인터셉터도 서블릿 필터와 같이 웹과 관련된 공통 관심 사항을 효과적으로 해결할 수 있는 기술이다.
 * 서블릿 필터가 서블릿이 제공하는 기능이라면, 스프링 인터셉터는 스프링 MVC가 제공하는 기능이다.
 *
 * HTTP 요청 -> WAS -> 필터 -> 서블릿 -> 스프링 인터셉터 -> 컨트롤러
 *
 * 스프링 인터셉터 제한
 * HTTP 요청 -> WAS -> 필터 -> 서블릿 -> 스프링 인터셉터 -> 컨트롤러 //로그인 사용자
 * HTTP 요청 -> WAS -> 필터 -> 서블릿 -> 스프링 인터셉터(적절하지 않은 요청이라 판단, 컨트롤러 호출X) // 비 로그인 사용자
 *
 * 스프링 인터셉터 체인
 * HTTP 요청 -> WAS -> 필터 -> 서블릿 -> 인터셉터1 -> 인터셉터2 -> 컨트롤러
 *
 * 스프링 인터셉터는 서블릿 필터보다 더 편리하고 다양한 기능을 제공한다.
 *
 * public interface HandlerInterceptor {
        default boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {}
        default void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {}
        default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {}
   }
 *
 * 서블릿 필터는 단순하게 doFilter만 제공한다면, 인터셉터는 컨트롤러 호출 전(preHandle), 호출 후(postHandle), 요청 완료 이후(afterCompletion)으로 세분화되어 있다.
 *
 * 컨트롤러에서 예외가 발생 시:
 * preHandle : 컨트롤러 호출 전에 호출되기 때문에 영향이 없다.
 * postHandle : 컨트롤러에서 예외가 발생하면 postHandle 은 호출되지 않는다.
 * afterCompletion : afterCompletion 은 항상 호출된다. 이 경우 예외( ex )를 파라미터로 받아서 어떤 예외가 발생했는지 로그로 출력할 수 있다.
 *
 * afterCompletion은 예외 발생여부와 관계없이 항상 호출되기 때문에, 예외와 무관하게 공통처리를 하려면 afterCompletion()을 사용해야 한다.
 *
 * 인터셉터에서 중요한 점은 메서드가 분리되어 있기 때문에 지역변수가 공유되지 않는다는 점이다.
 * 하지만 스프링 인터셉터도 싱글톤 빈으로 관리되기 때문에 멤버 변수를 추가하는 것은 좋은 선택이 아니다.
 * 이런 경우에는 request와 response에 Attribute를 추가함으로서 지역변수를 전달할 수 있다.
 *
 * 인터셉터도 Config 파일에서 스프링 빈으로 등록해야 한다.
 * 스프링 인터셉터는 WebMvcConfigurer 가 제공하는 addInterceptors() 를 사용해서 인터셉터를 등록할 수 있다.
 * 이때에도 서블릿 필터와 마찬가지로 인터셉터가 적용될 url, 적용되지 않을 url, order, 인터셉터 체인을 설정할 수 있다.
 *
 * 스프링 인터셉터를 활용해서 사용자 인증을 하기 위해서는 컨트롤러 호출 전인 preHandle에서 검증 로직을 구현하면 된다.
 *
 * (스프링 타입 컨버터)
 * HTTP 요청 파라미터는 모두 문자로 처리된다. 따라서 요청 파라미터를 자바에서 다른 타입으로 변환해서 사용하고 싶다면 타입 변환을 해줘야 한다.
 *
 * 스프링에서는 이 작업을 타입 컨버터가 자동으로 수행해준다.
 * 예: @RequestParam , @ModelAttribute , @PathVariable 등을 통해 파라미터값을 읽어 변수에 저장하거나 객체를 생성할 때
 *
 * 만약 개발자가 직접 타입 컨버터를 추가하고 싶다면 Converter 인터페이스를 구현하고 등록해주면 된다.
 * public interface Converter<S, T> {
 *      T convert(S source);
 * }
 *
 * 그런데 개발자가 여러개의 타입 컨버터를 구현해두고, 매번 적절한 타입 컨버터를 직접 찾아서 적용해주는 것은 매우 번거롭다.
 * 따라서 스프링에서는 여러 타입 컨버터를 한곳에 모아두고, 그것들을 묶어서 편리하게 사용할 수 있는 기능을 제공하는데, 이것이 바로 컨버전 서비스( ConversionService )이다.
 *
 * public interface ConversionService {
        boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType);
        boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType);
        <T> T convert(@Nullable Object source, Class<T> targetType);
        Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType);
    }
 *
 * ConversionService는 컨버팅이 가능한가 확인하는 기능과, 실제로 컨버팅을 수행해주는 기능을 제공한다.
 *
 * 스프링은 내부에서 ConversionService 를 제공한다.
 * 개발자는 WebMvcConfigurer가 제공하는 addFormatters() 를 사용해서 추가하고 싶은 컨버터를 등록하면 된다.
 *
 * ***JPA***
 * JDBC: 자바에서 DB에 연결하고, sql을 전달하고, 결과를 받을 수 있도록 지원하는 API
 * 즉 JAVA <-> JDBC <-> DB
 * 하지만 이렇게 JDBC API를 활용해서 DB에 접근하고 조작하는 것은 매우 번거로움
 *
 * JAVA와 DB의 근복적인 차이는 관계형 데이터베이스의 테이블과 객체의 구조나 관계, 특성이 다르다는 점이다.
 * 이 객체와 관계형 데이터베이스 간의 차이를 중간에서 해결해주는 프레임워크가 ORM이다(JPA도 여기에 포함)
 *
 * ORM을 사용하지 않으면 객체지향 언어(JAVA)를 사용하더라도, DB의 테이블의 구조에 의존적으로 로직을 짜야하는데,
 * ORM을 사용하게 되면 ORM이 객체와 관계형 데이터베이스간의 차이를 메꿔주기 때문에, 객체지향 언어의 장점을 그대로 살리면서 DB를 관리할 수 있다.
 *
 * JDBC를 사용할때에는 1. 개발자가 SQL을 직접 작성하고 2. JDBC API를 사용해서 SQL을 실행하고 3. SQL결과를 객체에 하나하나 매핑해야 한다.
 *
 * JDBC를 사용할때의 준제점은 데이터 접근 계층에 JDBC API나 SQL을 숨기더라도, 로직 자체가 SQL에 의존적이기 떄문에 새로운 기능을 추가할때마다 해당 SQL을 조작해줘야 한다.
 * 따라허 다음과 같은 문제가 존재한다
 * 1. 진정한 의미의 계층 분할이 어렵다
 * 2. 엔티티를 신뢰할 수 없다(엔티티뿐만 아니라 데이터 접근 계층을 분석해야 한다)
 * 3. SQL에 의존적인 개발을 피하기 어렵다
 *
 * 반면 JPA를 사용하면 단순히 JPA API를 활용하면 JPA 내부적으로 객체를 분석해서 적절한 SQL을 자동으로 생성해서 실행해준다.
 *
 * 객체지향 프로그래밍과 관계형 데이터베이스간의 가장 큰 차이점과 문제점은 패러다임의 불일치이다.
 * 객체지향 프로그래밍은 추상화, 캡슐화, 상속등 다양한 장치를 제공한다. 반면 관계형데이터베이스(테이블)은 이러한 객체와 구조가 달라서 이를 온전히 반영하기 어렵다.
 * 따라서 이 객체지향 언어와 관계형 데이터베이스간의 패러다임의 불일치를 개발자가 중간에서 해결해줘야 한다.
 *
 * JPA를 사용하면 다음과 같은 패러다임의 불일치를 해결해준다.
 * 1. 상속: jpa.persist()를 호출하여 자식 객체를 저장하는 경우, JPA는 내부적으로 부모 테이블, 자식 테이블에 나눠서 객체를 저장해준다. 이후 조회할때에는 부모 테이블과 자식테이블을 조인해서 결과를 객체에 매핑해준다.
 * 2. 연관관계: 객체에서의 연관관계는 참조값을 활용. 관계형 데이터베이스에서는 외래키와 조인을 활용. 또한 객체는 단방향이고, 테이블은 양방향 조회가 가능하다. JPA는 내부적으로 객체 내부에서의 참조값을 외리키 값으로 변환해준다.
 * 3. 객체 그래프 탐색: 객체는 참조값을 활용하여 연관된 객체를 마음껏 탐색할 수 있다. 반면 관계형 데이터베이스에서는 어떤 객체와 join하냐에 따라서 객체 그래프 탐색을 할 수 있는 범위가 제한된다. JPA에서는 지연 로딩을 통해서 실제 객체를 사용하는 시점까지 데이터베이스 조회를 마뤄서 자유로운 객체 그래프 탐색이 가능하다.
 * 4. 비교: 테이블은 Primary key 값을 기반으로 각 row를 구분한다. 반면 객체는 동일성 비교와 동등성 비교를 통해서 객체를 비교한다. JPA는 같은 트랜잭션일때, 같은 객체가 조회되는 것을 보장한다. 즉 동일한 Primary key로 조회한 경우, 동일한 인스턴스가 리턴되는 것을 보장한다.
 *
 * 결국 JPA가 하는 핵심적인 역할은 객체와 테이블간의 패러다임의 불일치를 개발자가 중간에서 메꾸는것이 아니라 JPA가 자동으로 수행해주는 것이다.
 * JPA는 JDBC와 애플리케이션 사이에서 동작한다. 즉 JPA가 적절한 JDBC API를 호출하는 역할을 한다
 *
 * 개발자는 자바 컬렉션에 저장하듯이 객체를 ORM 프레임워크에 저장 및 조회하게 되면, JPA가 내부적으로 엔티티를 분석해서 적절한 SQL을 생생해서 JDBC API를 호출해준다.(패러다임의 불일치를 해결)
 * JPA를 사용할때 가장 우려하는 점이 SQL 성능 최적화 측면인데, JPA에서는 native SQL을 통해서 개발자가 직접 SQL을 작성할 수 있는 기능도 제공하여 최적화도 가능하다.
 *
 * JPA를 사용하려면 객체와 테이블을 매핑해야 한다.
 * JPA에서는 @Entity나 @Table, @Column과 같은 매핑 에노테이션을 제공한다.
 *
 * @Entity는 이 클래스를 테이블과 매핑한다고 JPA에게 알려주는 역할을 한다(필수)
 * @Table은 엔티티 클래스에 매핑할 테이블 정보를 설정한다. 이때 기본값으로는 엔티티 이름을 테이블 이름으로 매핑한다.
 * @Id는 엔티티 클래스의 필드를 테이블의 기본 키에 매핑한다.(식별자 필드)
 * @Column는 엔티티 클래스의 필드를 테이블의 컬럼에 매핑한다. 이때 name 속성을 활용하면 테이블의 컬럼명을 설정할 수 있다.
 * 매핑 에노테이션을 생략하면 필드명을 기반으로 테이블의 컬럼과 매핑한다.
 *
 * JPA를 사용할때에는
 * 1. Persistence.createEntityManagerFactory를 통해서 EntityManagetFactory를 생성하고
 * 2. emf.createEntityManager를 통해서 EntityManager를 생성하고
 * 3. em.getTransaction을 통해서 EntityTransaction을 생성해야 한다.
 * JPA를 사용할때에는 항상 트랜잭션 내에서 데이터를 변경해야 한다!
 *
 * EntityManagerFactory는 DB 커넥션풀 생성 및 설정정보를 읽어야 하기 때문에 생성 비용이 크다 -> 하나만 생성 한 뒤 애플리케이션 전체에서 공유해야 함
 * JPA의 기능 대부분은 EntityManaget가 제공한다. 엔티티매니저는 내부에 데이터소스(데이터베이스 커넥션)이 존재하며, 이를 통해 데이터베이스와 통신한다.
 *
 * 또한 JPA에서는 객체 조회 시 JPQL이라는 쿼리 언어를 제공한다.
 * JPQL: 엔티티 객체를 대상으로 쿼리
 * SQL: 데이터베이스 테이블을 대상으로 쿼리
 *
 * 하나의 웹 애플리케이션에서는 하나의 엔티티매니저팩토리를 생성해서 이를 공유해서 사용하며, 이를 통해 엔티티 매니저를 생성한다.
 * 그리고 엔티티 매니저 팩토리는 스레드안전하기 때문에 여러 스레드에서 공유해도 되지만, 엔티티 메니저는 스레드안전하지 않기 때문에 여러 스레드간에 공유하면 안된다.
 *
 * JPA에서는 EntityManagerFactory를 생성할때 DB 커넥션 풀도 생성하는데, 이 커넥션 풀은 엔티티 매니저들이 트랜잭션을 시작할때 해당 커넥션 풀에서 DB 커넥션 하나를 얻는다.
 *
 * *영속성 컨텍스트 (Persistence Context)*
 * JPA를 사용하게 되면 엔티티 매니저를 활용하여 엔티티를 저장하거나 조회하게 되는데, 엔티티 매니저로 엔티티를 저장하거나 조회하면 엔티티 매니저는 영속성 컨텍스트에 엔티티를 보관하고 관리한다.
 * 영속성 컨텍스트는 엔티티 매니저를 생성할 때 하나 만들어진다. 그리고 엔티티 매니저를 통해서 영속성 컨텍스트에 접근할 수 있고, 영속성 컨텍스트를 관리할 수 있다.
 *
 * 영속성 컨텍스트는 실제로 엔티티를 저장하고 관리하는 환경이고, 엔티티 매니저는 영속성 컨텍스트를 운영할 수 있는 일종의 API
 *
 * 엔티티 생명주기
 * 1. 비영속(new/transient): 영속성 컨텍스트와 전혀 관계가 없는 상태 -> em.persist를 통해 영속성 컨텍스트에 등록하기 전
 * 2. 영속(managed): 영속성 컨텍스트에 저장된 상태
 * 3. 준영속(detached): 영속성 컨텍스트에 저장되었다가 분리된 상태
 * 4. 삭제(removed): 삭제된 상태
 *
 * 영속성 컨텍스트의 특징
 * 1. 영속성 컨텍스트에서는 엔티티를 식별자 값(@Id)로 구분한다. 따라서 영속 상태의 엔티티는 식별자 값이 반드시 있어야 한다.
 * 2. 영속성 컨텍스트에서 관리되고 있는 엔티티들은 트랜잭션을 커밋하는 순간에 데이터베이스에 반영하는데, 이를 flush라고 한다.
 * 3. 영속성 컨텍스트에서 엔티티를 관리하면 다음과 같은 장점이 있다.
 *  - 1차 캐시
 *  - 동일성 보장
 *  - 트랜잭션을 지원하는 쓰기 지연
 *  - 변경 감지
 *  - 지연 로딩
 * 4. 영속성 컨텍스트는 보통 트랜잭션의 생존 주기와 동기화 되기 때문에 영속성 컨텍스트를 사용하기 위해서는 트랜잭션을 시작해야 한다.
 *
 * 영속성 컨텍스트 내부에는 캐시가 존재하는데, 이를 1차 캐시라고 한다.영속 상태의 엔티티들은 이 1차 캐시에서 관리된다.(일종의 Map이고, 키는 @Id, 값은 엔티티 인스턴스)
 * 이 1차 캐시를 활용해서 동일한 @Id의 엔티티를 조회할때 동일한 인스턴스를 리턴하면서 동일성을 보장할 수 있다.
 *
 * 영속성 컨텍스트에는 내부 쿼리 저장소가 존재하고, 해당 SQL 저장소에 INSERT, UPDATE, DELETE SQL등을 저장해둔다. 이후 Transaction이 커밋되는 시점에 한번에 쿼리를 데이터베이스에 보낸다. 이를 트랜잭션을 지원하는 쓰기 지연이라고 한다.
 *
 * 영속성 컨텍스트에서는 변경 감지를 지원한다.(dirty checking)
 *
 * ***스냅샷과 변경감지***
 * 엔티티를 영속성 컨텍스트에 보관할 때, 최초 상태를 복사해서 저장해두는데, 이를 스냅샷이라고 한다.
 * 그리고 flush 시점에 스냅샷과 엔티티를 비교해서 변경된 엔티티를 찾고, 변경점을 찾아서 update 쿼리를 생성해서 쓰기 지연 SQL 저장소에 보낸다.
 * 이후 쓰기 지연 SQL 저장소에 있는 쿼리들을 한번에 데이터베이스에 보낸다.
 *
 * 이 변경감지 덕분에 엔티티 객체를 수정하기만 해도 플러시 시점에 수정사항이 DB에 반영된다.
 * 이 변경감지는 영속성 컨텍스트에서 관리되는 엔티티만 해당하기 때문에 영속상태여야 한다.
 *
 * remove도 마찬가지로 쓰기 지연 SQL 저장소에 DELETE SQL이 보관되며, flush 시점에 적용된다. 다만 영속성 컨텍스트의 1차 캐시에서는 remove를 호출한 시점에 삭제된다.
 *
 * 영속성 컨텍스트를 플러시하는 방법은 다음과 같은 방법들이 있다.
 * 1. em.flush()를 직접 호출한다.
 * 2. 트랜잭션 커밋 시 플러시가 자동 호출된다.
 * 3. JPQL 쿼리 실행 시 플러시가 자동 호출된다. -> JPQL은 JPA를 거치지 않고 바로 DB에 접근하기 때문에, 영속성 컨텍스트와 DB간의 데이터 일관성을 위해서 JPQL 쿼리 실행시 플러시가 자동 호출된다.
 *
 * 엔티티가 준영속 상태가 되는 경우는 다음과 같다.
 * 1. em.detact(entity): 특정 엔티티만 준영속 상태로 전환한다. 이때 영속성 컨텍스트의 1차 캐시나 SQL 보관소에서 해당 엔티티와 관련된 정보들이 모두 삭제된다. 즉 지금까지의 변경사항들도 모두 삭제된다.
 * 2. em.clear(): 영속성 컨텍스트를 완전히 초기화한다.
 * 3. em.close(): 영속성 컨텍스트를 종료한다.
 *
 * *** 엔티티와 테이블 매핑***
 * JPA에서 제공하는 다양한 매핑 에노테이션
 * 1. 객체와 테이블 매핑: @Entity, @Table
 * 2. 기본 키 매핑: @Id
 * 3. 필드와 컬럼 매핑: @Column
 * 4. 연관관계 매핑: @ManyToOne, @JoinColumn -> 매우 중요
 *
 * 엔티티로 관리하고 테이블과 매핑할 객체는 @Entity 에노테이션을 필수적으로 붙여야 한다. @Entity 적용 시 주의사항은 다음과 같다.
 * 1. 기본 생성자는 필수다(파라미터가 없는 public 또는 protected 생성자)
 * 2. final 클래스, enum, interface, inner 클래스에는 사용할 수 없다.
 * 3. 저장할 필드에 final을 사용하면 안된다.
 *
 * 데이터베이스 스키마 자동생성은 데이터베이스에 테이블을 미리 생성해둘 필요 없이, 엔티티의 매핑 정보를 기반으로 JPA가 데이터베이스 스키마를 자동으로 생성하는 방법이다.
 * @Column의 매핑정보의 nullable, length와 같은 속성 정보들을 추가해서 제약조건을 추가할 수 있다.
 * 또한 @Table에서 uniqueConstraints 속성을 사용하면 유니크 제약조건을 추가할 수 있다. 다만 이런 제약 조건들은 DDL 자동생성 기능을 사용할때만 적용된다.(Table 스키마 처음생성하는 시점)
 *
 * @Id를 사용해서 키본키를 매핑할 때 애플리케이션에서 직접 값을 할당할 수도 있고, 대리 키를 사용할 수 있다.
 * 자동생성 전략을 사용하려면 @GeneratedValue를 추가하고, 키 생성 전략을 사용해야 한다.
 *
 * IDENTITY: 기본 키 생성을 데이터베이스에 위임한다. (MYSQL의 AUTO_INCRETNET) -> 쓰기 지연 발생 x
 * SEQUENCE: 데이터베이스 시퀀스를 사용해서 기본 키를 할당한다.
 * TABLE: 키 생성 테이블을 사용한다.
 *
 * 직접 할당 방식을 사용하게 되면 영속성 컨텍스트에 엔티티를 등록하기 전에 직접 기본키 필드를 채워야 한다.(1차 캐시에서 기본키를 기반으로 엔티티를 구분하기 때문에)
 * IDENTITY 방식을 사용하면 데이터베이스에 엔티티를 저장해야 기본 키 필드가 설정된다. -> 따라서 이 경우에는 쓰기 지연이 적용되지 않으며, DB에 엔티티를 저장한 다음, 기본 키 필드가 설정되면 그 다음에 영속성 컨텍스트에 등록된다.
 *
 * 테이블(엔티티)의 기본 키를 선택하는 전략은 크게 2가지가 존재한다.
 * 1. 자연 키(natural key): 비즈니스에 의미가 있는 키
 * 2. 대리 키(surrogate key): 비즈니스와 관련없는 임의로 만들어진 키, 대체 키로도 불린다.
 *
 * 기본적으로는 자연 키보다 대리키를 권장한다.(비즈니스 환경이 변하게 되면 비즈니스에 의미가 있는 자연 키는 변해야되는 경우가 생길수도 있고, 중복이 되는 경우도 생길 수 있음)
 *
 * 그리고 @Transient 에노테이션을 사용하면 해당 필드는 테이블에 매핑되지 않는다.
 *
 * @Column의 unique 속성을 설정하면 유니크 제약조건을 추가할 수 있다. 다만 여러 field를 묶어서 유니크 제약조건을 추가하고 싶은 경우에는 @Table의 uniqueConstraints 속성을 사용해야 한다.
 * 데이터베이스 스키마 자동 생성 기능을 활용하면 엔티티 객체를 먼저 만들고 테이블은 엔티티 매핑정보를 기반으로 자동으로 생성된다.
 *
 * 엔티티의 필드에서 다른 엔티티와의 연관관계를 설정하기 위해서는 연관관계 매핑이 필수적이다. 이 연관관계 매핑은 JPA에서 가장 중요한 부분중 하나이다.
 *
 * ***연관관계 매핑***
 * 객체는 다른 객체와 참조를 통해서 관계를 맺는 반면, 테이블은 외래 키를 통해서 관계를 맺는다. 이 둘의 차이를 메워야 한다.
 * 또한 객체는 관계의 방향이 존재(단방향)하지만, 테이블 연관관계는 항상 양방향이다. 만약 객체를 양방향으로 만들고 싶다면 양쪽 객체에서 서로의 참조값을 가져 단방향 관계 2개를 구축해야 한다.
 *
 * 그리고 객체는 참조를 통해서 연관관계를 탐색할 수 있는데, 이것을 객체 그래프 탐색이라고 한다.
 *
 * JPA를 활용해서 연관관계를 매핑하기 위해서는 @ManyToOne, @ManyToMany, @OneToMany, @OneToOne을 사용해야 한다.
 * 연관관계 매핑 에노테이션을 사용하면 연관관계 참조와 해당 연관관계의 외래 키를 매핑해준다.
 * 이때 @JoinColumn()을 사용해서 해당 연관관계의 참조와 외래 키를 매핑한다.
 *
 * @JoinColumn()은 연관관계 참조와 외래키를 매핑할 때 사용한다. 다음과 같은 속성이 존재한다.
 * 1. name: 매핑할 외래 키 이름
 * 2. referenceColumnName: 외래 키가 참조하는 대상 테이블의 컬럼명
 * 3. foreignKey(DDL): 외래 키 제약조건
 * JoinColumn은 생략할 수 있으며 기본값은 필드명 + _ + 참조하는 테이블의 컬럼명이 된다.
 *
 * 연관관계를 설정할때에는 참조할 엔티티도 영속상태여야 한다.
 *
 * 연관된 엔티티를 삭제할때에는 기존에 있던 연관관계를 모두 먼저 제거해야 한다. 그러지 않으면 외래 키 제약조건으로 인해 데이터베이스에서 오류가 발생한다.
 *
 * 엔티티에서 양방향 연관관계를 설정하기 위해서는 각 엔티티에서 서로에 대한 참조값을 가지면 된다.
 *
 * [양방향 연관관계]
 * @OneToMany의 경우에는 여러 연관관계를 관리해야 하기 때문에 컬렉션을 사용해야 한다.
 *
 * 양방향 연관관계에서는 연관관계의 주인을 설정해줘야 한다.
 * [연관관계의 주인]
 * 테이블에서는 외래 키 하나로 양방향 조회가 가능하다.
 * 반면 엔티티는 단방향 2개를 연결한 구조이기 때문에, 양쪽 객체에 참조가 존재해서 총 2개의 참조가 존재한다.
 * 하지만 테이블은 하나의 외래 키로 연관관계가 설정되기 때문에, 두 참조중 어떤 참조를 외래 키와 매핑할지를 결정해야 한다.
 *
 * 따라서 양방향 연관관계 매핑 시에는 반드시 두 연관관계 중 하나를 연관관계의 주인으로 정해야 한다.
 * 연관관계의 주인만이 데이터베이스 연관관계와 매핑되고, 외래 키를 관리할 수 있고, 연관관계의 주인이 아닌 쪽은 외래 키를 읽기만 할 수 있다.
 * 즉 외래 키가 어떤 엔티티의 테이블에 속할지를 결정한다(연관관계의 주인)
 *
 * 이때 연관관계의 주인은 mappedBy 속성을 사용하지 않는다.
 * 연관관계의 주인이 아니면 mappedBy 속성을 사용해서 속성의 값으로 연관관계의 주인을 지정해야 한다.
 * 즉 mappedBy가 없는 쪽이 연관관계의 주인이며, 외래 키를 관리하고, mappedBy가 있는 쪽은 주인이 아니며, 외래 키를 읽기만 가능하다.
 *
 * 연관관계의 주인만 외래 키를 관리할 수 있기 때문에, 연관관계의 주인은 외래 키를 가지고 있는 엔티티(테이블)로 설정해야 한다.
 *
 * 개념적으로는 연관관계의 주인과 외래 키의 위치가 다른 개념이지만 실무적으로는 항상 외래 키가 있는 쪽을 연관관계의 주인으로 설정한다.
 * 즉 @JoinColumn을 설정해서 외래 키를 엔티티에 설정했다면, @JoinColumn이 있는 쪽을 연관관계의 주인으로 설정해야 한다.
 *
 * 만약 @JoinColumn을 설정하지 않으면 JPA가 자동으로 연관관계의 주인인 엔티티에 외래키를 추가해준다.
 *
 * 이때 데이터베이스 테이블의 다대일, 일대다 관계에서는 항상 다 쪽이 외래 키를 가진다. -> 다쪽은 구조상 외래 키를 설정할 수 없음(PK가 중복됨)
 * 따라서 항상 다쪽이 연관관계의 주인이 되기 때문에 @ManyToOne 에는 mappedBy 속성이 없다.
 *
 * 연관관계의 주인 측에서 연관관계를 설정하게 되면 외래 키가 등록되기 때문에 비주인쪽에서 명시적으로 연관관계를 설정할 필요가 없다.
 *
 * JPA를 사용하면 연관관계의 주인 쪽에서 연관관계를 설정하면 비주인쪽에 대해서는 연관관계가 자동으로 주입되지만, 안전하게 양방향 모두에 연관관계를 주입해주는 것이 안전하다.
 *
 * 따라서 양방향 연관관계의 경우에는 양방향에서 연관관계를 설정해주는것이 안전하기 때문에 연관관계 편의 메서드에서 양쪽 연관관계를 설정해주는 것이 안전하다.
 * 그리고 양방향 연관관계 편의 메서드를 구현할때, 기존에 매핑되어 있던 연관관계가 있다면 이를 해제해줘야 한다.
 *
 * 단방향 연관관계에 비해서 양방향 연관관계가 훨신 복잡하고 어렵다. 따라서 일단은 단방향 연관관계를 사용하고, 필요하다면 양방향 연관관계를 추가한다.
 *
 * 일대일 매핑에서는 양쪽 누구든 상관없이 외래 키를 가질 수 있다. 따라서 일대일 관계에서는 주 테이블과 대상 테이블 중 누가 외래 키를 가질 지 선택해야 한다.
 *
 * 관계형 데이터베이스는 테이블 2개로 다대다 관계를 표현할 수 없다. (하나의 row에서 여러 FK값을 가질 수 없다)
 * 따라서 다대다 관계를 일대다, 다대일 관계로 풀어내는 연결 테이블을 사용한다.
 * 반면 객체는 두개의 엔티티간의 다대다 관계를 컬렉션을 통해서 구현할 수 있다.
 *
 * 연결 테이블을 사용하는 경우에는 @ManyToMany와 @JoinTable을 사용해서 연결 테이블을 바로 매핑할 수 있는데, 이 경우에는 중간 연결 엔티티가 생성되지 않는다.
 * @JoinTable은 연결 테이블명과 속성을 설정한다.
 *
 * 다만 @ManyToMany와 @JoinTable을 사용하면 연결 엔티티를 생성하지 않으므로 추가 정보를 관리할 수 없다.
 * 따라서 @ManyToMany가 아니라 연결 엔티티를 추가해서 @OneToMany, @ManyToOne으로 분해하는 것을 권장한다.
 *
 * @IdClass(식별자 클래스)를 사용하면 복합 기본 키를 사용할 수 있다.
 *
 * 식별 관계: 받아온 식별자를 기본 키 + 외래 키로 사용한다.(연결 테이블에서 외래 키들을 묶어서 기본 키로 사용)
 * 비식별 관계: 받아온 식별자는 외래 키로만 사용하고 새로운 식별자(대리 키)를 추가한다.
 *
 * [상속관계 매핑]
 * 관계형 데이터베이스에는 상속이라는 개념이 없음. 대신 슈퍼타입 / 서브 타입이 존재
 * JPA는 엔티티간의 상속관계를 테이블의 슈퍼타입 / 서브타입으로 매핑해준다.
 *
 * 슈퍼타입 / 서브타입을 모델링할 때에는 다음과 같은 방법이 있다.
 * 1. 조인 전략(부모 테이블과 자식 테이블 모두 생성)
 * 2. 단일 테이블 전략(한 테이블에 모든 필드정보를 추가)
 * 3. 구현 클래스마다 테이블 전략(자식 클래스마다 테이블을 생성하고, 부모 클래스의 필드를 추가)
 *
 * 상속관계를 매핑하기 위해서는 @Inheritance(strategy = InheritanceType.JOINED)와 같이 타입을 설정할 수 있다.
 * 그리고 @DiscriminatorColumn(name = "DTYPE")과 같이 자식 테이블의 타입을 설정할 수 있는 컬럼을 추가해야 한다.
 *
 * 이 방식들은 부모 클래스와 자식 클래스 모두 데이터베이스의 테이블과 매핑하는 방식인데,
 * 부모 클래스는 테이블과 매핑하지 않고, 자식 클래스만 매핑하고 싶은 경우에는 @MappedSuperClass를 사용한다.
 *
 * [식별관계와 비식별 관계]
 * 식별 관계에서는 부모 테이블의 기본 키를 내려 받아 자식테이블의 기본 키 + 외래 키로 사용하는 방식이다.
 * JPA에서 식별자를 둘 이상 사용하려면 별도의 식별자 클래스를 만들어야 한다.
 *
 * JPA에서 영속성 컨텍스트에서 엔티티를 관리할때에는 식별자를 통해서 엔티티를 구분하는데,
 * 식별자 클래스를 사용하게 되면, 해당 식별자를 기반으로 동등성 비교를 수행하기 대문에 식별자 클래스에서 equals와 hashcode를 구현해야 한다.
 *
 * 식별자 클래스를 사용하는 경우에는 엔티티를 조회할 때에도 식별자 클래스를 생성하고, 식별자 클래스를 기반으로 엔티티를 조회해야 한다.
 *
 * 식별관계의 문제점은 부모 테이블의 기본 키가 복합 키라면, 자식 테이블의 외래 키도 복합키로 설정해야 하기 때문에 매우 복잡해진다는 문제가 존재한다.
 * 또한 별도의 식별자 클래스도 생성해야 하기 때문에 매우 번거롭다. 따라서 기본적으로는 비식별관계를 사용하는 것이 좋디.
 *
 * [프록시와 연관관계 정리]
 * JPA에서 가장 중요한 개념이 프록시이다.
 * 엔티티를 조회할 때, 연관된 엔티티들이 항상 사용되는 것은 아니다. 따라서 엔티티를 조회할때 연관된 엔티티를 실제로 사용하는 시점까지 로딩을 미루는 것을 지연 로딩이라고 한다.
 *
 * 이렇게 지연 로딩 기능을 사용하려면, 실제 엔티티 객체 대신에 데이터베이스 조회를 지연할 수 있는 가짜 객체를 생성해서 주입해야 하는데, 이를 프록시 객체라고 한다.
 *
 * 즉시 로딩: em.find() 엔티티가 영속성 컨텍스트에 없으면 DB에서 즉시 엔티티 조회
 * 지연 로딩: em.getReference() 엔티티가 영속성 컨텍스트에 없으면 DB에서 즉시 엔티티를 조회하는 것이 아니라, 프록시 객체(가짜 객체)를 생성해서 반환한다.
 *
 * 프록시 클래스는 실제 클래스를 상속받아서 만들어지며, 실제 객체에 대한 참조값을 보관한다. 따라서 프록시 객체의 메서드를 호출하면 프록시 객체는 실제 객체의 참조값을 통해서 실제 객체의 메서드를 호출한다.
 *
 * 프록시 객체 초기화는 실제 엔티티의 데이터에 접근할때 일어나며, 이때 DB에서 엔티티를 조회한 후, 프록시 객체 내부에 실제 객체에 대한 참조값을 보관한다.
 * 프록시 객체 초기화는 영속성 컨텍스트의 도움을 받기 때문에, 프록시 객체를 초기화하기 위해서는 영속상태여야 한다.
 *
 * [즉시 로딩과 지연 로딩]
 * JPA에서는 연관된 엔티티의 조회 시점을 매핑 에노테이션의 속성을 활용해서 설정할 수 있다.
 * 즉시 로딩: @ManyToOne(fetch = FetchType.EAGER)
 * 지연 로딩: @ManyToOne(fetch = FetchType.LAZY)
 *
 * 기본값:
 * @ManyToOne: EAGER
 * @OneToOne: EAGER
 * @OneToMany: LAZY
 * @ManyToMany: LAZY
 *
 * @OneToMany와 같이 연관된 엔티티가 여러개일 경우 즉시 로딩하게 되면 비용이 크기 때문에 실제 데이터 접근시에 loading하는 LAZY loading이 기본값으로 설정되어 있다.
 * JPA의 기본 fetch 전략은 연관된 엔티티가 하나면(일대일, 다대일) 즉시 로딩을, 컬렉션(일대다, 다대다)이면 지연 로딩을 사용한다.
 *
 * 엔티티를 지연 로딩하면 프록시 객체를 사용해서 지연 로딩을 수행하지만, 컬렉션의 경우에는 컬렉션 래퍼가 지연 로딩을 처리해준다.
 *
 * 실무적으로는 모든 연관관계를 지연 로딩으로 설정하고, 필요한 부분에서만 즉시 로딩을 사용하는 것을 권장한다(N+1문제)
 *
 * 컬렉션에 FetchType.EAGER 사용 시 주의점
 * 컬렉션에 FetchType.EAGER을 사용하여 즉시 로딩을 사용할 경우에는 다음과 같은 주의점이 있다.
 * 컬렉션을 하나 이상 즉시 로딩하는 것은 권장하지 않는다.
 * 컬렉션과 조인한다는 것은, 데이터베이스 테이블로 보면 일대다 조인을 수행한다는 의미이다. 일대다 조인은 결과 데이터가 다 쪽에 있는 데이터의 수만큼 증가하게 된다.

 * 문제는 서로 다른 컬렉션을 2개 이상 조인할 때 발생한다.
 * 예를 들어 A 테이블에 N,M 두 테이블과 일대다 연관관계가 형성되어 있고, A 테이블에서 이 두 테이블과 조인을 수행하게 되면, 결과 데이터의 수가 N * M이 되면서 너무 많은 데이터를 반환할 수 있고, 결과적으로 애플리케이션 성능이 저하될 수 있다.
 *
 * 컬렉션 즉시 로딩은 항상 외부 조인을 사용한다.
 * 회원 테이블과 팀 테이블은 다대일 연관관계를 형성한다. 이때 회원 테이블의 외래 키에 Not Null 제약조건을 추가하면, 모든 회원은 팀에 소속되기 때문에, 항상 내부 조인을 사용해도 누락되는 회원 데이터없이 데이터를 조회할 수 있다.

 * 반면에 팀 테이블에서 회원 테이블로 일대다 관계를 조회할 때에는 회원이 한명도 속하지 않은 팀을 내부 조인하면 팀까지 조회되지 않는 문제가 발생한다. 따라서 일대다 관계를 조인할 때에는 항상 외부 조인을 사용하게 된다.
 *
 * 따라서 컬렉션은 지연 로딩을 사용하는 것을 권장한다.
 *
 * [영속성 전이와 CASCADE]
 * 특정 엔티티를 영속 상태로 만들 때, 연관된 엔티티도 함께 영속 상태로 만들고 싶을 때에는 영속성 전이 기능을 사용하면 된다.
 *
 * JPA에서 엔티티를 저장할 때에는 연관된 모든 엔티티는 영속 상태여야 한다.
 * 즉 JPA에서 엔티티를 저장할 때에는 FK가 참조하는 대상 엔티티를 먼저 영속 상태로 등록하고, 이후 FK를 가진 엔티티를 등록해야 한다.
 *
 * 영속성 전이는 @OneToMany(mappedBy = "parent", cascade = CascadeType.PERSIST) 와 같이 설정할 수 있다.
 *
 * 영속성 전이를 사용하지 않으면 연관된 엔티티 등록 및 삭제를 모두 일일히 설정해줘야 하는데, 영속성 전이를 사용하면 부모 엔티티만 설정하면 된다.
 *
 * CASCADE에는 다음과 같은 타입이 존재
 * public enum CascadeType{
	ALL,		// 모두 적용
    PERSIST, 	// 영속
    MERGE,		// 병합
    REMOVE,		// 삭제
    REFRESH,	// REFRESH
    DETACH		// DETACH
    }
 *
 * [고아 객체 삭제]
 * JPA에서는 부모 엔티티와 연관관계가 끊어진 자식 엔티티를 자동으로 삭제하는 기능을 제공하는데, 이것을 고아 객체 제거라고 한다.
 * 이 기능을 사용하면 부모 엔티티의 컬렉션에서 자식 엔티티의 참조만 제거하면 자식 엔티티가 자동으로 삭제된다.
 * @OneToMany(mappedBy = "parent", orphanRemoval = true)
 * private List<Child> children = new ArrayList<Child>
 *
 * 고아 객체 삭제는 다음과 같이 orphanRemoval = true로 설정하면 되고, 이 경우에 해당 컬렉션에서 자식 엔티티가 제거되면 자동으로 자동 엔티티가 영속성 컨텍스트 및 DB에서 제거된다.
 *
 * [엔티티 생명 주기 관리]
 * 영속성 전이 + 고아 객체, 생명주기
 * 일반적으로 엔티티는 EntityManager.persist()를 통해 영속화되고, EntityManager.remove()를 통해 제거된다. 이것은 엔티티 스스로 생명주기를 관리한다는 의미이다.
 * 반면에 CascadeType.ALL + orphanRemoval = true를 동시에 사용한다면, 부모 엔티티를 통해서 자식 엔티티의 생명주기를 관리할 수 있다.
 * 부모를 등록하면 자식도 함께 등록되며, 자식을 삭제하기 위해서는 연관관계를 제거하거나, 부모를 제거하면 된다.
 * 즉 CascadeType.ALL + orphanRemoval = true를 사용하면 부모 엔티티를 통해서 자식 엔티티의 생명주기를 관리할 수 있다.
 *
 * 즉 자식 엔티티의 생명주기를 직접 건드리지 않고, 부모 엔티티을 통해서 자식 엔티티의 생명주기를 관리할 수 있다.
 *
 * 다만 여기서 부모 엔티티와 자식 엔티티는 상속관계가 아니라, 비즈니스 로직 상 생명주기가 종속되는 것을 의미한다.
 *
 * [객체지향 쿼리 언어]
 * JPA에서 제공하는 em.find()와 같은 조회 메서드는 결국 엔티티의 식별자를 기반으로 엔티티를 조회한다.
 * 따라서 복잡한 조회 조건을 사용하기 위해서는 개발자가 객체지향 쿼리 언어를 직접 작성해야 한다.
 *
 * SQL: 데이터베이스의 테이블을 대상으로 하는 데이터 중심의 쿼리이다.
 * JPQL: 엔티티 객체를 대상으로 쿼리를 작성하는 방법을 제공
 *
 * 개발자가 JPQL로 쿼리를 작성하게 되면, JPA가 내부적으로 Database 방언을 기반으로 적절한 SQL로 변환하여 DB에 전달하게 된다.
 *
 * 작성한 JPQL을 실행하기 위해서는 쿼리 객체를 생성해야 한다. 쿼리 객체의 종류로는 다음이 있다.
 * TypedQuery: 조회 대상의 타입을 하나로 지정할 수 있으면 해당 쿼리 객체를 사용한다.
 * Query: 조회 대상의 타입을 하나로 지정할 수 없거나, 조회 대상의 타입을 알 수 없는 경우 해당 쿼리 객체를 사용한다.
 *
 * 코드 예시:
 * TypedQuery<Member> query = em.createQuery("SELECT m FROM Member m", Member.class);
 * Query query = em.createQuery("SELECT m.username, m.age from Member m");
 *
 * JPQL에서는 위치 기반 파라미터 바인딩과 이름 기반 파라미터 바인딩을 모두 지원한다. 이중에서는 이름 기반 파라미터 바인딩이 더 안전하다.
 *
 * ***파라미터 바인딩의 장점***
 *
 * 파라미터 바인딩을 사용하지 않고 아래와 같이 직접 문자열을 더하는 방식을 사용할 수도 있다.
 * "SELECT m FROM Member m where m.username = '" + usernameParam +"'"
 *
 * 하지만 이러한 방식은 SQL 인젝션에 매우 취약하다.
 * SQL 인젝션이란 공격자가 악의적으로 ; DROP TABLE users; -- 와 같이 SQL문을 조작하는 것을 의미한다.
 *
 * 따라서 사용자가 입력값에 SQL 조작 코드를 작성해버리면 SQL 코드로 인식되는 심각한 보안상의 위험이 발생한다.
 *
 * 파라미터 바인딩 방식은 쿼리와 값을 분리하는 방식이다.
 * 파라미터는 값으로만 취급되어, 파라미터로 SQL문법을 전달하여도, 이것이 SQL 문법으로 인식되지 않아 SQL 인젝션에 상대적으로 안전하다.
 *
 * [페이징 API]
 * 데이터베이스에 많은 데이터를 저장하는 경우, 데이터베이스에서 데이터들을 특정 단위로 쪼개서 조회해야 하는 경우가 많다.
 *
 * 다만 데이터베이스마다 페이징을 지원하는 SQL 문법이 너무 다르기 때문에, JPA에서는 페이징을 다음 두 API로 추상화하였다.
 *
 * setFirstResult(int startPosition): 조회 시작 위치(0부터 시작한다)
 * setMaxResults(int maxResult): 조회할 데이터 수
 * TypedQuery<Member> query = em.createQuery("SELECT m FROM Member m ORDER BY m.username DESC", Member.class);
 * query.setFirstResult(10); 페이징 API 활용
 * query.setMaxResults(20);
 * query.getResultList();
 *
 * 페이징 API를 활용하면 특정 SQL 문법에 의존하지 않고 페이징 기능을 손쉽게 구현할 수 있다.
 *
 * SQL에서는 어떤 테이블과 조인을 해도 상관없지만, JPQL에서는 조인을 할때, 항상 연관 필드를 사용해야 한다. 이 말은 JPQL에서는 연관된 엔티티를 대상으로만 조인을 할 수 있다는 의미이다.
 *
 * JPQL 조인을 활용해서 연관된 엔티티를 함께 조회하였을때, 두 엔티티가 모두 영속성 컨텍스트에 등록되지만, JPA 객체 간 연관관계를 주입해주지는 않는다.
 * 이 경우에는 개발자가 직접 조회한 엔티티들의 연관관계를 주입해줘야 한다.
 *
 * 이렇게 연관관계에 있는 엔티티를 함께 조회했을때 자동으로 의존관계가 주입되지 않으면 개발자가 직접 의존관계를 주입해줘야 하는 번거로움이 발생한다.
 * 만약 엔티티를 조회하면서, 연관관계도 함께 주입하고 싶은 경우에는 패치 조인(fetch join) 을 사용해야 한다.
 *
 * [Fetch Join] -> 패치조인은 항상 즉시 로딩
 * select m from Member m join fetch m.team
 *
 * Fetch join을 사용하면 엔티티를 조회할때 연관관계의 엔티티도 함께 주입해준다.
 *
 * 연관관계를 지연 로딩으로 설정해도, JPQL에서 패치조인으로 엔티티를 조회하면, 연관된 엔티티를 즉시 로딩하기 때문에 즉시 로딩이 수행된다.
 *
 * 패치 조인을 사용하면 편리하기는 하지만 즉시 로딩이 수행된다는 단점이 있다.
 * 따라서 글로벌 패치 전략은 항상 지연로딩으로 설정하고, 꼭 필요한 부분애서만 패치 조인을 사용해서 즉시 로딩을 수행하는 것이 좋다.
 *
 * [벌크 연산]
 * 영속성 컨텍스트의 변경 감지를 활용해서 엔티티를 하나하나 수정하는 것이 아니라,
 * JPLQ을 활용해서 여러 엔티티를 한번에 수정하거나 삭제할 수 있는 벌크연산을 사용함으로서 최적화할 수 있다.
 * int resultCount = em.createQuery(qlString)
					.setParameter("stockAmount", 10)
                    .executeUpdate();
 * 벌크 연산을 수핼하기 위해서는 executeUpdate()를 호출하면 된다.
 *
 * 다만 벌크 연산은 JPQL을 활용하며, 영속성 컨텍스트를 거치지 않고, 데이터베이스에 직접 쿼리하기 때문에 매우매우 주의해야 한다.
 * 벌크 연산을 잘못 사용하면 영속성 컨텍스트를 잘 못 사용하면 영속성 컨텍스트와 DB간의 데이터 무결성이 깨지는 심각한 문제가 발생할 수 있다.
 *
 * 이렇게 벌크 연산으로 인한 영속성 컨텍스트와 DB간의 데이터 일관성이 깨지는 문제는 다음과 같은 방법들로 해결할 수 있다.
 * 1. 벌크 연산을 수행한 직후에 em.refresh()를 통해서 DB에서 엔티티를 다시 조회해서 영속성 컨텍스트에 등록
 * 2. 영속성 컨텍스트에서 엔티티를 조회하기 전에, 벌크 연산을 가장 먼저 실행.(가장 권장)
 * 3. 벌크 연산 수행 후 영속성 컨텍스트 초기화
 *
 * JPQL을 통해 엔티티를 조회하였는데, 만약 조회한 엔티티가 이미 영속성 컨텍스트에 등록되어 있는경우, JPA는 조회한 엔티티를 제거하고, 영속성 컨텍스트에서 해당 엔티티를 개발자에게 반환하게 된다.
 *
 * [JPQL과 플러시 모드]
 * 플러시란 영속성 컨텍스트에서 쓰기 지연, 변경 감지를 통해 추적하던 변경사항을 데이터베이스에 동기화하는 것이다.
 * JPA는 플러시가 일어날 때 영속성 컨텍스트에 등록,수정,삭제한 엔티티를 찾아서 적절한 SQL을 생성하여 데이터베이스에 반영한다.
 *
 * 플러시 모드에는 AUTO와 COMMIT이 있다.
 * AUTO: 기본값이며, 트랜잭션이 커밋되는 시점 또는 쿼리 실행 직전에 자동으로 플러시를 호출한다. JPQL 실행시 자동으로 플러시된다.
 * COMMIT: 쿼리 실행 시점에는 플러시 되지 않고, 트랜잭션이 커밋되는 시점에만 플러시를 호출한다. JPQL 실행시 플러시되지 않는다.
 *
 * JPQL은 영속성 컨텍스트를 거치지 않고 바로 DB에 접근하는 방법이기 때문에 플러시를 하지 않으면 영속성 컨텍스트에서 추적중이던 변경사항과 불일치가 발생할 수 있다.
 * 따라서 JPQL을 실행하기 전에는 항상 영속성 컨텍스트의 내용을 DB에 반영해야 하며, 이때문에 플러시 모드의 기본값이 AUTO이다.
 *
 * COMMIT 모드를 사용하면 플러시 횟수를 줄여 성능을 최적화할 수는 있지만, 영속성 컨텍스트와 DB간의 불일치로 인한 문제가 발생할 수 있다.
 *
 * [JPQL 쿼리 빌더와 Native Query]
 * QueryDSL: JPQL을 편리하게 작성할 수 있도록 도와주는 JPQL 쿼리 빌더
 * Native Query: JPA에서 JPQL이 아닌 SQL을 직접 사용할 수 있도록 지원하는 기술
 *
 * 개발자가 문자열을 기반으로 JPQL을 작성하게되면 오타나 문법 오류가 발생하기 쉬움
 * 따라서 Criteria나 QueryDSL과 같은 QueryBuilder는 문자가 아니라 코드 기반으로 JPQL을 작성할 수 있도록 지원하는 기술들이다.
 *
 * QueryDSL을 사용할 때 가장 큰 장점은, QueryDSL은 여러 편의 메서드를 제공하여, 동적 쿼리를 매우 편리하게 작성할 수 있다는 점이다.
 * 동적 쿼리를 작성해야 하는 경우에는 QueryDSL이 매우 편리하다.
 *
 * public void queryDSL() {

    EntityManager em = emf.createEntityManager();

    JPAQuery query = new JPAQuery(em);
    QMember qMember = new QMember("m"); //생성되는 JPQL의 별칭 지정

    List<Member> members =
    	query.from(qMember)
        	 .where(qMember.name.eq("회원1"))
             .orderBy(qMember.name.desc())
             .list(qMember);
    }
 * QueryDSL 코드 예시를 보면, 문자가 아니라 코드 기반으로 JPQL을 쉽게 작성할 수 있다.
 *
 * QueryDSL을 사용하기 위해서는 쿼리 타입(QType) 객체를 생성해야 한다.
 * 쿼리 타입 객체란, 실제 엔티티를 기반으로 만들어지는 객체인데, 엔티티와, 내부 필드에 대한 메타데이터를 보관하여, 필드값에 타입안전하게 접근할 수 있으며, 다양한 편의 메서드들을 추가하여, 사용자가 쉽게 JPQL 쿼리를 작성할 수 있도록 도와주는 객체이다.
 *
 * 쿼리 타입 객체는 JPQL 쿼리 작성을 위한 다양한 편의 메서드가 추가된다.
 * QItem item = QItem.item; //기본 인스턴스 사용
 * 또한 쿼리 타입 객체는 내부에 기본 인스턴스가 존재하기 때문에 이를 활용해도 된다.
 *
 * QItem item = QItem.item;

    query.from(item)
	 .where(item.price.gt(20000))
     .orderBy(item.price.desc(), item.stockQuantity.asc())
     .offset(10).limit(20)
     .list(item);
 *
 * QueryDSL을 사용하면 offset()과 limit()을 활용해서 페이징 기능을 쉽게 구현할 수 있다. offset은 조회 시작 위치, limit은 조회할 데이터 수를 지정한다.
 *
 * query.from(order)
	.innerJoin(order,member, member).fetch()
    .leftJoin(order.orderItems, orderItem).fetch()
    .list(order);
 * 또한 QueryDSL에서도 fetch() 메서드를 통해서 패치 조인을 할 수 있다.
 *
 * QItem item = QItem.item;
   JPAUpdateClause updateClause = new JPAUpdateClause(em, item);

   long count = updateClause.where(item.name.eq("시골개발자의 JPA 책"))
						 .set(item.price, item.price.add(100))
                         .execute();
 * 또한 QueryDSL을 활용해서도 벌크 연산을 수행할 수 있다.
 * QueryDSL을 활용하여 벌크 연산을 수행할때에도 JPQL과 마찬가지로, 영속성 컨텍스트와 DB간의 데이터불일치가 발생할 수 있다는 문제가 존재한다.
 *
 * QueryDSL을 사용하는 가장 큰 이유 중 하나는 동적 쿼리이다.
 * 동적 쿼리란 조건에 따라서 쿼리가 달라지는 것을 의미한다. 이를 개발자가 JPQL을 문자기반으로 직접 구현하려면 수많은 if문을 활용해야 한다.
 *
 * SearchParam param = new SearchParam();
   param.setName("시골개발자");
   param.setPrice(10000);

   QItem item = QItem.item;

   BooleanBuilder builder = new BooleanBuilder();
   if (StringUtils.hasText(param.getName())) {
	builder.and(item.name.contains(param.getName()));
   }
   if(param.getPrice() != null){
	bulider.and(item.price.gt(param.getPrice()));
   }

   List<Item> result = query.from(item)
	.where(builder)
    .list(item);
 *
 * QueryDSL을 활용하여 동적 쿼리를 작성할 때에는 BooleanBuilder를 활용하면 된다. 그러면 BooleanBuilder 내부에서 조건에 따라 and를 추가해주거나, where문을 추가해주는 등의 작업을 수행해준다.
 *
 * [네이티브 SQL]
 * JPQL은 표준 SQL이 지원하는 대부분의 문법과 SQL 함수들을 지원하지만, 특정 데이터베이스에 종속정인 기능은 지원하지 않는다.
 * 따라서 특정 데이터베이스에 종속적인 기능을 사용하고 싶은 경우에는 네이티브 SQL을 통해 SQL을 개발자가 직접 작성하여 DB에 전송할 수 있는 기능을 제공한다.
 *
 * JDBC API와 네이티브 SQL과의 차이점:
 * JDBC API는 JPA와 독립적인 기술이기 때문에 영속성 컨텍스트의 도움을 받을 수 없다.
 * 반면 네이티브 SQL은 JPA에 종속적인 기술이기 때문에 영속성 컨텍스트의 도움을 받을 수 있다.
 *
 * String sql = "SELECT ID, AGE, NAME, TEAM_ID " +
			 "FROM MEMBER WHERE AGE > ?";

   Query nativeQuery = em.createNativeQuery(sql, Member.class)
					  .setParameter(1, 20);

   List<Member> resultList = nativeQuery.getResultList();
 *
 * 네이티브 SQL을 사용하기 위해서는 createNativeQuery를 활용하면 된다.
 *
 * [스프링 데이터 JPA]
 * 스프링에서는 JPA를 편리하게 사용할 수 있도록 지원하는 스프링 데이터 JPA를 지원한다.
 * 이를 활용하면 JPA를 정말 편리하게 사용할 수 있다.
 *
 * JPA를 사용하면 각 Repository마다 기본적인 CRUD 기능들을 반복적으로 구현해야 하는 문제점이 있다.
 * 스프링 데이터 JPA에서는 데이터 접근 계층을 개발할 때 지루하게 반복되는 CRUD 기능들을 공통 인터페이스에 구현해두었다
 * 따라서 개발자는 데이터 접근 계층을 개발할때 해당 인터페이스를 상속받기만 하면 손쉽게 CRUD 기능을 사용할 수 있다.
 * public interface MemberRepository extends JpaRepository<Member, Long>{
	Member findByUsername(String username);
   }

   public interface interface ItemRepository extends JpaRepository<Item, Long> {
   }
 * 스프링 데이터 JPA에서는 기본 CRUD 기능이 공통 인터페이스인 JpaRepository에 구현되어 있다. 따라서 해당 인터페이스를 상속받기만 해도 된다.
 * 이렇게 인터페이스를 설정하면,  애플리케이션 실행 시점에 스프링 데이터 JPA가 해당 인터페이스에 대한 구현 클래스를 동적으로 생성하여 스프링 빈으로 등록해준다.
 *
 * 스프링 데이터 JPA에서 가장 핵심적인 기술이라고 하면 쿼리 메서드가 있다.
 * 쿼리 메서드 기능을 활용하면 인터페이스만으로도 필요한 대부분의 쿼리 기능을 개발할 수 있게된다.
 *
 * [쿼리 메서드]
 * 스프링 데이터 JPA는 애플리케이션 실행 시점에, 인터페이스에 정의된 메서드 명을 분석하여 구현 클래스에 적절한 쿼리를 생성하여 제공한다.
 * public interface MemberRepository extends JpaRepository<Member, Long> {
	List<Member> findByEmailAndName(String email, String name);
   }
 * 따라서 개발자는 메서드 명만 잘 지으면 JPQL을 개발자가 직접 구현하지 않아도 스프링 데이터 JPA가 자동으로 쿼리를 생성해준다.
 * 또한 @Param()을 사용하면 이름 기반 파라미터 바인딩도 가능하다.
 *
 * 만약 개발자가 직접 쿼리를 작성하고 싶은 경우에는
 * public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query(value = "SELECT * FROM MEMBER WHERE USERNAME = ?0",
    	nativeQuery = true)
    Member findByUsername(String username);
   }
 * 와 같이 @Query 에노테이션 안에 쿼리를 직접 작성할 수도 있다.
 * 또한 nativeQuery 속성을 활용하여 nativeQuery 사용 여부도 결정할 수 있다.
 *
 * @Modifying
 * @Query("update Product p set p.price = p.price * 1.1 where p.stockAmount < :stockAmount")
   int bulkPriceUp (@Param("stockAmount") String stockAmount);
 *
 * 또한 스프링 데이터 JPA에서 벌크 연산을 수행하고 싶은 경우에는 @Modifying 에노테이션을 사용하면 된다.
 *
 * 스프링 데이터 JPA에서는 페이징과 정렬 기능을 제공하기 위해서 두가지 파라미터를 제공한다.
 * Sort: 조회결과를 정렬할 때 사용한다.
 * Pageable: 페이징 기능을 사용할 때 사용한다. -> 구현체로는 보통 PageRequest를 사용한다.
 *
 * PageRequest pageRequest = new PageRequest(0, 10, new Sort(Direction.DESC, "name"));

   Page<Member> result = memberRepository.findByNameStartingWith("김",pageRequest);

   List<Member> members = result.getContent(); //조회된 실제 데이터 반환
   int totalPages = result.getTotalPages(); // 전체 페이지수
   boolean hasNextPage = result.hasNextPage(); //다음 페이지 존재 여부
 *
 * PageRequest에는 현재 페이지 번호, 조회할 데이터수, 정렬 조건을 지정할 수 있다.
 *
 * 하지만 이렇게 인터페이스와 쿼리 메서드를 사용하면 인터페이스를 정의하는 것만으로도 대부분의 기능을 구현할 수는 있지만, 사용자 정의 메서드는 로직을 작성할 수 없다.
 * 이때에는 해당 인터페이스를 구현하는 구현 클래스를 추가해서 해당 클래스에 사용자 정의 메서드 로직을 추가하면 된다.
 *
 * 스프링 데이터 JPA에서 QueryDSL을 사용하는 방법:
 * 1. QueryDslPredicateExecutor 인터페이스를 상속
 * 2. QueryDslRepositorySupport 클래스 상속
 *
 * 스프링 환경에서 JPA를 사용하면 스프링 컨테이너가 트랜잭션과 영속성 컨텍스트를 관리해준다.
 *
 * 스프링 컨테이너는 트랜잭션 범위의 영속성 컨텍스트 전략을 기본으로 사용한다. 이는 트랜잭션의 생존범위와 영속성 컨텍스트의 생존범위가 동일하다는 의미이다.
 * 또한 같은 트랜잭션 내부에서는 항상 같은 영속성 컨텍스트에 접근한다.
 *
 * 일반적으로 스프링 프레임워크를 사용하여 웹 애플리케이션을 개발할 때에는 비즈니스 로직 단위로 트랜잭션을 적용하기 위해 서비스 계층에서 @Transactional 에노테이션을 사용하여 트랜잭션을 시작하는 것이 일반적이다.
 * 서비스 계층에 @Transactional 에노테이션을 사용하면, 스프링은 트랜잭션 AOP를 활용하여 서비스 계층에 대한 프록시 객체를 생성하고, 해당 프록시 객체에서 트랜잭션을 시작하고, 실제 서비스 계층의 메서드를 호출하여 실행하고, 실행 결과에 따라 트랜잭션을 커밋/롤백하는 역할을 수행한다.
 * 따라서 서비스 계층에서 @Transactional 에노테이션을 설정하면 프록시 객체가 생성되며, 해당 프록시 객체가 트랜잭션 시작, 커밋/롤백을 수행해준다.
 * 그리고 트랜잭션 범위의 영속성 컨텍스트의 경우에는 트랜잭션의 범위와 영속성 컨텍스트의 범위가 동일하다.
 *
 * 이렇게 트랜잭션 AOP를 활용하게 되면, 트랜잭션의 범위가 서비스 계층과, 서비스 계층에서 사용하는 레파지터리 계층까지로 설정된다.
 * 보통 컨트롤러는 서비스 계층의 외부에 존재하기 때문에, 컨트롤러는 트랜잭션의 범위에 포함되지 않고, 따라서 컨트롤러 계층에서 서비스 계층의 메서드를 호출해서 엔티티를 반환받으면 해당 엔티티는 준영속상태가 된다.
 *
 * [준영속 상태와 지연로딩]
 * 보통 애플리케이션 흐름은 컨트롤러 -> 서비스(트랜잭션 시작 및 종료) -> 레파지터리 구조이며, 트랜잭션의 범위는 서비스 및 레파지터리로 설정된다.
 * 따라서 컨트롤러 계층에서 엔티티를 반환받으면, 해당 엔티티는 준영속상태가 된다.
 * 만약 이경우에 컨트롤러 계층에서 지연로딩을 수행하게 되면 영속성 컨텍스트의 도움을 받을 수 없기 때문에 예외가 발생한다.
 *
 * class OrderController {

    public String view(Long orderId) {

        //서비스 계층의 메서드를 호출하여 엔티티반환, 해당 엔티티는 준영속 상태이다.
        Order order = orderService.findOne(orderId);

        //order 엔티티는 준영속 상태이므로 변경감지가 수행되지 않는다.
		order.setPrice(10000);

        Member member = order.getMember();

        //준영속 상태에서는 지연 로딩을 수행할 수 없다.
        member.getName(); //LazyInitializationException 발생
    }
   }
 * 이렇게 준영속 상태에서 지연 로딩을 수행할 때(프록시 객체 초기화를 준영속 상태에서 수행) 발생하는 예외가 LazyInitializationException이다.
 * 심지어 컨트롤러 뿐만 아니라 뷰에서, 반환받은 엔티티뿐만 아니라, 연관된 엔티티도 함께 렌더링해야 하는 경우가 생길 수 있다.
 * 이때 서비스 계층에서 프록시 객체 초기화를 수행하지 않았다면 뷰에서 연관된 엔티티의 데이터를 조회하는 시점에, 프록시 객체 초기화를 시도하지만, 영속성 컨텍스트가 이미 종료된 상태이기 때문에 실패하고, 예외가 발생한다.
 * 즉 예외가 퍼질 수 있고, 이 경우에는 원인을 추적하기 어려워진다.
 *
 * 준영속 상태의 지연 로딩 문제를 해결하는 방법에는 크게 두가지가 있다.
 * 1. 뷰가 필요한 엔티티를 서비스 계층에서 미리 로딩하는 방법(프록시 객체 초기화를 서비스계층에서 미리 수행)
 * 2. OSIV를 사용하여 엔티티를 항상 영속 상태로 유지하는 방법
 *
 * 이때 서비스 계층에서 미리 로딩하는 방법은 크게 3가지가 있다.
 * 1. 글로벌 패치 전략 수정(즉시 로딩)(fetch = FetchType.EAGER)
 * 2. JPQL 패치 조인(즉시 로딩)
 * 3. 강제로 초기화(지연 로딩) -> Fascade 계층을 추가해서 해당 계층에서 프록시 객체 초기화를 수행한 다음, 컨트롤러에 엔티티를 반환하는 방법
 *      즉 Controller -> Fascade(트랜잭션 시작 / 종료 + 프록시 객체 초기화) -> Service -> Repository 구조가 된다.
 *
 * 하지만 글로벌 패치 전략을 즉시 로딩으로 설정하면 심각한 문제가 발생할 수 있다.
 * 1. 사용하지 않는 연관 엔티티까지 모두 함께 로드한다.
 * 2. N+1 문제가 발생한다.
 *
 * [N+1 문제]
 * 즉시 로딩시에 JPA의 메서드 em.find()등을 호출하면 내부적으로 join을 통해서 연관된 엔티티를 한번에 함께 조회한다.
 * 반면 즉시 로딩시에 JPQL을 통해서 엔티티를 조회하게 되면 join을 사용하지 않고 엔티티를 하나하나 조회하게 되고, 이것이 N+1 문제이다.
 *
 * 예시:
 * List<Order> orders =
	em.createQuery("select o from Order o", Order.class)
     .getResultList(); //연관된 모든 엔티티를 조회한다.
 *
 * select * from Order // JPQL로 실행된 SQL
   select * from Member where id=? //즉시 로딩에 의해 추가로 실행된 SQL
   select * from Member where id=? //즉시 로딩에 의해 추가로 실행된 SQL
   select * from Member where id=? //즉시 로딩에 의해 추가로 실행된 SQL
   select * from Member where id=? //즉시 로딩에 의해 추가로 실행된 SQL
   ....
 *
 * JPQL을 통해서 엔티티를 조회할때에는 JPA가 글로벌 패치 전략을 고려하지 않고, JPQL만을 보고 엔티티를 조회한다.
 * 그 이후 글로벌 패치 전략을 보고, 연관된 엔티티를 하나하나 조회하게 되고, 이것이 N+1문제이다.
 *
 * 스프링 데이터 JPA가 제공하는 쿼리 메서드나 다양한 편의 기능들도 내부적으로는 JPQL로 변환되어 실행된다. 따라서 스프링 데이터 JPA를 사용하더라도 N+1문제는 그대로 발생한다.
 *
 * 이 N+1 문제를 해결하는 방법은 지연 로딩 + 필요한 부분에서 패치 조인하는 것이 바람직하다.
 *
 * (패치 조인을 사용하지 않는 경우)
 * JPQL: select o from Order o
   SQL: select * from Order
 *
 * (패치 조인을 사용하는 경우)
 * JPQL: select o from Order o join fetch o.member
   SQL: select o.*, m.* from Order o join Member m on o.MEMBER_ID=m.MEMBER_ID
 *
 * 이처럼 패치조인을 사용하게 되면 즉시 로딩을 수행하면서도 내부적으로 join을 통해서 연관된 엔티티를 한번에 로딩하기 때문에 N+1 문제가 발생하지 않는다.
 *
 * (정리)
 * N+1 문제의 원인은 즉시 로딩시에 Join을 통해서 연관된 엔티티를 한번에 로딩하냐 / 아니면 연관된 엔티티를 하나하나 조회하냐의 차이이다.
 * 이를 해결하기 위해서는 지연 로딩을 사용하거나, 패치 조인을 사용해서 내부적으로 Join을 통해 한번에 로드하면 된다.
 *
 * 결국 이 준영속 상태에서의 지연로딩 문제가 발생하는 원인은 영속성 컨텍스트가 서비스 계층까지만 생존하기 때문에 프레젠테이션 계층에서는 영속성 컨텍스트를 사용할 수 없기 때문에 발생한다.
 * OSIV는 영속성 컨텍스트를 서비스계층 뿐만 아니라, 프레젠테이션 계층까지 확장하여 생존할 수 있도록 지원하는 기술이다.
 *
 * [OSIV]
 * OSIV(Open Session In View)는 영속성 컨텍스트를 뷰까지 열어둔다는 의미이다.
 * 영속성 컨텍스트가 뷰까지 살아있기 때문에 엔티티가 항상 영속상태이므로 어디에서든 프록시 객체 초기화가 가능해진다.
 *
 * 요청 당 트랜잭션 방식의 OSIV: 서블릿 필터나 스프링 인터셉터에서 트랜잭션을 시작하고 종료하는 방식
 *
 * 하지만 요청 당 트랜잭션 방식의 OSIV는 크게 두가지 문제가 존재한다.
 * 1. 트랜잭션의 범위가 너무 넓다. (트랜잭션은 원래 비즈니스 로직 단위로 설정하는 것이 바람직하다)
 * 2. 프레젠테이션 계층에서 엔티티를 변경할 수 있다.
 *
 * 이러한 문제를 해결하기 위해 스프링 OSIV가 등장함
 * [스프링 OSIV]
 * 스프링 프레임워크에서 제공하는 OSIV는 트랜잭션의 범위를 서비스 계층과 레파지터리 계층으로 한정하면서, 영속성 컨텍스트의 생존 범위는 프레젠테이션 계층까지 확장하는 방식이다.
 * 이때 OSIV를 서블릿 필터에 적용할지, 스프링 인터셉터에 적용할지에 따라 원하는 클래스를 선택해서 사용할 수 있다.
 *
 * 1. 클라이언트의 요청이 들어오면 서블릿 필터나, 스프링 인터셉터에서 영속성 컨텍스트를 생성한다. 이때 트랜잭션은 시작하지 않는다.
 * 2. 서비스 계층에서 @Transactional로 트랜잭션을 시작할 때, 서블릿 필터나 스프링 인터셉터에서 생성한 영속성 컨텍스트를 사용하여 트랜잭션을 시작한다.
 * 3. 서비스 계층이 끝나면 트랜잭션을 커밋하고, 영속성 컨텍스트를 플러시한다. 이때 트랜잭션을 커밋하면 트랜잭션은 종료되지만, 영송성 컨텍스트는 종료되지 않는다.
 * 4.이제 프레젠테이션 계층까지 영속성 컨텍스트가 유지되기 때문에, 프레젠테이션 계층에서 조회한 엔티티는 영속상태가 된다. 단 이때 트랜잭션은 이미 종료 되었다.
 * 5.서블릿 필터나 스프링 인터셉터로 요청이 돌아오면 영속성 컨텍스트를 종료한다. 이때 플러시를 호출하지 않고 바로 종료한다.
 *
 * 영속성 컨텍스트는 트랜잭션 범위 안에서만 엔티티를 조회하고 수정할 수 있다.
 * 영속성 컨텍스트는 트랜잭션 범위 밖에서는 엔티티를 조회만 할 수 있으며, 이를 트랜잭션 없이 읽기 라고 한다.
 *
 * 컨트롤러나 뷰에서 엔티티를 수정한다고 하더라도, 스프링 인터셉터에서 영속성 컨텍스트를 종료할 때 플러시를 호출하지 않기 때문에 변경사항이 반영되지 않는다.
 *
 * [래퍼 컬렉션]
 * JPA의 구현체인 하이버네이트는 엔티티를 영속상태로 만들 때, 컬렉션 필드를 하이버네이트에서 제공하는 컬렉션으로 감싸서 사용한다.
 *
 * 이때 데이터의 순서를 관리하는 방법으로는 @OrderColumn과 @OrderBy가 있다.
 * @OrderColumn: 테이블에서 데이터의 순서정보를 관리하기 위한 추가적인 컬럼을 추가 -> 이는 추가적인 쿼리가 발생할 수 있으며 중간에 데이터가 삭제되면 순서정보를 모두 업데이트 해줘야 하는 문제가 존재
 * @OrderBy: 테이블에는 순서정보 저장 x. 조회시에만 해당 기준을 기반으로 데이터를 조회(데이터베이스의 OrderBy 절을 사용)
 *
 * @Converter를 사용하면 엔티티의 필드 값이나 타입을 변환하여 데이터베이스의 테이블에 저장할 수 있다.
 *
 * [리스너]
 * JPA에서는 엔티티 엔티티의 생명주기에 따라 이벤트가 발생하며 엔티티의 상태나 시점에 따라서 이벤트의 종류가 달라진다.
 * 이때 JPA 리스너 기능을 사용하면, 엔티티의 생명주기에 따른 이벤트가 발생할 때, 로그를 기록하거나 특정 작업을 수행할 수 있다.
 *
 * @Entity
public class Duck {

    @Id @GeneratedValue
    public Long id;

    @PrePersist
    public void prePersist(){
    	System.out.println("Duck.prePersist id=" + id);
    }

    @PostPersist
    public void postPersist(){
    	System.out.println("Duck.postPersist id=" + id);
    }
 * 리스너는 엔티티 안에서 에노테이션을 적용하여 직접 설정할 수도 있다.
 *
 * @Entity
@EntityListeners(DuckListener.class)
public class Duck {

}

public class DuckListener {

    @PrePersist
    private void prePersist(Object obj) {
    	System.out.println("DuckListener.prePersist obj = [" + obj + "]");
    }

    @PostPersist
    private void postPersist(Object obj) {
    	System.out.println("DuckListener.postPersist obj = [" + obj + "]");
    }
}
 * 또는 리스너 엔티티를 구현하고, 이를 엔티티에 등록할 수도 있다.
 *
 * [엔티티 그래프]
 * 엔티티 그래프 기능은 엔티티 조회시점에 연관된 엔티티를 함께 조회하는 기능이다.
 *
 * 패치조인을 사용하면 글로벌 패치 전략이 지연로딩이여도 즉시 연관된 엔티티를 함께 조회할 수 있다.
 * 하지만 연관된 엔티티가 하나가 아니라 여러개라면 각각의 엔티티마다 패치조인 쿼리를 반복적으로 작성해야 하는 단점이 존재한다.
 *
 * 이때 엔티티 그래프 기능을 사용하면, 엔티티를 조회하는 시점에 함께 조회할 연관된 엔티티를 선택할 수 있다.
 * 엔티티 그래프 기능은 엔티티 조회시점에 연관된 엔티티를 함께 조회하는 기능이다.
 *
 * 엔티티그래프는 정적으로 정의하는 Named 엔티티 그래프와 동적으로 정의하는 엔티티 그래프가 있다.
 *
 * Named 엔티티 그래프:
 * @NamedEntityGraph(name = "Order.withMember", attributeNodes = {
	@NameAttributeNode("member")
    })
    @Entity
    @Table(name = "ORDERS")
    public class Order {

        @Id @GeneratedValue
        @Column(name = "ORDER_ID")
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "MEMBER_ID")
        private Member member;
    }
 *
 * name 속성을 통해 엔티티그래프의 이름을 지정하고, @NameAttributeNode 속성을 통해 함께 조회할 엔티티를 지정한다.
 *
 *  EntityGraph graph = em.getEntityGraph("Order.withMember");

    Map hints = new HashMap();
    hints.put("javax.persistence.fetchgraph", graph);

    Order order = em.find(Order.class, orderId, hints);
 *
 * 엔티티 그래프를 사용할때에는 EntityGraph를 활용해서 엔티티그래프를 생성하고, 쿼리 힌트 정보에 엔티티 그래프를 추가하면된다.
 *
 * 이렇게 엔티티매니저의 메서드를 호출할 수도 있지만, JPQL을 작성할때 쿼리 힌트로 엔티티 매니저를 추가할 수도 있다.
 * List<Order> resultList =
	em.createQuery("select o from Order o where o.id = :orderId", Order.class)
    .setParameter("orderId", orderId)
    .setHint("javax.persistence.fetchgraph", em.getEntityGraph("Order.withMember"))
    .getResultList();
 *
 * 즉 엔티티 그래프는 이 엔티티를 조회할때 어떤 엔티티를 함께 조회할지를 알려주는 fetch plan이다.
 * JPA가 이를 보고 적절한 SQL을 생성하여 연관된 엔티티를 함께 조인해서 반환한다.
 * 만약 엔티티 그래프에 설정한 엔티티가 여러개라면, JPA는 JPQL -> SQL 변환과정에서 여러 엔티티를 JOIN해서 함께 조회한다.
 *
 * 만약 Order -> OrderItem -> Item 까지 함께 조회하고 싶으면 Subgraph를 활용해야 한다.
 * @NamedEntityGraph(name = "Order.withAll", attributeNodes = {
	@NamedAttributeNode("member"),
    @NamedAttributeNode(value = "orderItems", subgraph = "orderItems"),
    subgraphs = @NamedSubgraph(name = "orderItems", attributeNodes = {
    	@NamedAttributeNode("item")
    })
   }
 * subgraph를 활용하면 엔티티 그래프를 체인 형태로 연결할 수 있게 된다.
 *
 * 동적 엔티티 그래프:
 * 엔티티 그래프를 동적으로 구성하려면 createEntityGraph() 메서드를 사용하면 된다.
 *  EntityGraph<Order> graph = em.createEntityGraph(Order.class);
    graph.addAttributeNodes("member");
    Subgraph<OrderItem> orderItems = graph.addSubgraph("orderItems");
    orderItems.addAttributeNodes("item");

    Map hints = new HashMap();
    hints.put("javax.persistence.fetchgraph", graph);

    Order order = em.find(Order.class, orderId, hints);
 *
 * 동적 엔티티 그래프를 구축할 때에도 subgraph를 활용할 수 있다.
 *
 * 기본적으로는 지연 로딩 + 패치 조인을 사용하되, 패치 조인용 SQL 작성도 반복된다면 엔티티 그래프를 고려
 * 엔티티 그래프는 별다른 게 아니라, 어떤 엔티티를 함께 조회할지 JPA에게 알려주는 일종의 fetch hint를 구축하는것이다.
 *
 * JPA 표준 예외는 javax.persistence.PersistenceException의 자식 클래스이며, 이는 언체크 예외이다.
 * JPA를 통해 구현한 레파지터리 계층에서 발생하는 JPA 표준 예외를 서비스 계층에서 그대로 받아서 처리한다면, 서비스 계층이 결과적으로 JPA 기술에 의존적이게 된다.
 * 이는 서비스 계층이 레파지터리 계층에 의존하게 되는 문제가 발생한다. 따라서 스프링에서는 JPA 표준 예외를 스프링 표준 예외로 변환해주는 기능을 제공한다.
 *
 * @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
	return new PersistenceExceptionTranslationPostProcessor();
    }
 * JPA 표준 예외를 스프링 표준 예외로 변환하기 위헤서는 다음과 같은 예외 변환기를 스프링 빈으로 등록하면 된다.
 * 이렇게 예외 변환기를 스프링 빈으로 등록하게 되면, @Repository 에노테이션을 사용한 곳에서 변환 AOP를 적용하여 JPA 예외를 스프링 표준 예외로 변환해준다.
 *
 * [트랜잭션 롤백과 변경감지]
 * 트랜잭션이 롤백되면 데이터베이스의 반영사항만 롤백하는 것이지, 영속성 컨텍스트에서 수행중이던 변경감지까지 초기화 되는 것은 아니다.
 * 따라서 한 트랜잭션에서 롤백을 하더라도, 다른 트랜잭션에서 커밋을 하면 변경사항이 반영될 수 있다는 문제가 있다.
 * 따라서 트랜잭션 롤백시에는 영속성 컨텍스트를 초기화 해주는 것이 안전하다.
 *
 * 스프링 OSIV를 사용하는 경우에는 트랜잭션이 롤백되더라도, 영속성 컨텍스트가 프레젠테이션 계층까지 살아있기 때문에 문제가 발생할 수 있다.
 * 따라서 스프링 OSIV에서는 트랜잭션을 롤백하게 되면 자동으로 영속성 컨텍스트를 초기화해준다.
 *
 * [영속성 컨텍스트와 동일성 비교]
 * 영속성 컨텍스트는 각자 고유한 1차 캐시를 보유하고, 해당 1차 캐시내에서 엔티티를 관리한다.
 * 따라서 동일한 영속성컨텍스트를 사용하여 동일한 엔티티를 조회하게 되면 동일한 인스턴스를 반환받아 동일성 비교에 성공한다.
 * 하지만 서로 다른 영속성 컨텍스트를 사용하여 동일한 엔티티를 조회하게 되면, 각자의 1차 캐시에 서로 다른 인스턴스가 생성되어 동일성 비교에 실패한다.
 *
 * 스프링 OSIV를 사용한다면 클라이언트의 요청 시작과 종료시점까지 영속성 컨텍스트가 살아있기 때문에 동일한 영속성 컨텍스트를 사용하여 이 문제가 줄어든다.
 * 반면 트랜잭션당 영속성 컨텍스트 전략에서는 트랜잭션이 커밋/롤백될때마다 영속성 컨텍스트가 생성/삭재되어 동일성 문제가 발생할 수 있다.
 *
 * 따라서 이러한 점때문에 엔티티를 비교할대에는 eqauls와 hashcode를 오버라이딩하여 동등성 비교하는 것을 권장한다.
 *
 * [프록시 심화주제]
 * 영속성 컨텍스트는 프록시로 조회된 엔티티에 대해서도 동일성을 보장하기 위해서, 영속성 컨텍스트에 프록시 객체를 저장한 경우에, 동일한 엔티티를 조회하는 요청이 오면, 원본 엔티티를 반환하는 것이 아니라 영속성 컨텍스트에서 관리중인 프록시 객체를 반환한다
 * 즉 이미 영속성 컨텍스트에 프록시 객체가 등록되어 있다면, 원본 엔티티를 조회하더라도 원본 엔티티가 영속성 컨텍스트에 등록되는 것이 아니라, 프록시 객체를 반환하게 된다.
 * 따라서 프록시로 조회해도 영속성 컨텍스트는 영속 엔티티의 동일성을 보장한다.(프록시 인스턴스를 저장하고 반환해준다.)
 *
 * 반대로 원본 엔티티를 먼저 조회하고, 프록시를 조회하는 경우에는 원본 엔티티가 이미 영속성 컨텍스트에 존재하기 때문에 프록시 객체를 반환받는것이 아니라 1차 캐시 내의 원본 엔티티를 반환하게 된다.
 * 따라서 이 경우에도 영속성 컨텍스트는 영속 엔티티의 동일성을 보장한다. (원본 엔티티의 인스턴스를 저장하고 반환해준다.)
 *
 * [프록시 객체의 타입 비교]
 * 프록시 객체의 경우 원본 엔티티를 상속받아서 만들어지기 때문에 원본 엔티티 타입과 프록시 객체의 타입을 비교할때 == 비교를 하면 틀리게 되고, 따라서 instanceof를 사용해야 한다.
 * 즉 원본 엔티티의 클래스 타입과 프록시 객체의 클래스 타입은 부모 자식 관계이기 때문에, 프록시 객체의 클래스 타입을 비교할 때에는 == 비교가 아니라 instanceof 비교를 수행해야 한다.
 *
 * [프록시 객체의 동등성 비교]
 * 엔티티의 동등성을 비교하려면 비즈니스 키를 사용하여 equals() 메서드를 오버라이딩하면 된다.
 * 하지만 IDE나 외부 라이브러리를 통해 자동으로 구현된 equals() 메서드로 엔티티를 비교하는 경우에, 비교 대상이 원본 엔티티의 경우에는 동등성 비교가 정상적으로 수행되지만, 비교 대상이 프록시 객체인 경우에는 문제가 발생하여 동등성 비교가 항상 실패하게 된다.
 *
 *  @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if (obj == null) return null;
        if (this.getClass() != obj.getClass()) return false; // --- 1. 문제

        Member member = (Member) obj;

        if(name != null ? !name.equals(member.name) :
           member.name != null)
           return false; // --- 2. 문제

        return true;
    }
 *
 * 인텔리제이에서 자동으로 구현하는 equals 메서드를 보면, 두가지 문제가 존재한다.
 * 1. 엔티티 타입을 == 비교 -> 원본 엔티티의 경우에는 문제가 발생하지 않지만, 프록시 객체의 경우 문제가 발생한다. -> 이를 instanceof로 변환해야 한다.
 * 2. 두번째 문제는 비교 대상의 필드값을 직접 비교하는 방식이다. 원본 엔티티는 필드값에 데이터가 저장되어 있기 때문에 문제가 발생하지 않는다.
 *    하지만 프록시 객체의 경우에는 프록시 객체 초기화가 발생하기 전까지는 필드값이 모두 null로 설정되어 있다. 따라서 이 경우에는 동등성 비교에 실패하게 된다.
 *    따라서 이 문제를 해결하기 위해서는 필드값에 직접 접근하는 것이 아니라, getter 메서드를 호출하면 프록시 객체 초기화가 일어나면서 원본 엔티티의 필드값을 return받게 되며, 필드값이 채워지기 때문에 동등성 비교를 수행할 수 있다.
 *  Member member = (Member) obj;

    if (name != null ? !name.equals(member.getName()) :
        member.getName() != null)
        return false;
 *
 * 수정된 equals() 메서드 예시
 *  @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if (obj == null) return null;
        if(!(obj instanceof Member)) return false;

        Member member = (Member) obj;

        if (name != null ? !name.equals(member.getName()) :
        member.getName() != null)
        return false;

        return true;
    }
 *
 * 정리하자면 프록시 객체의 동등성을 비교할 때에는 다음 사항을 주의해야 한다.
 * 1. 프록시 객체의 타입 비교는 == 비교 대신에 instanceof를 사용해야 한다.
 * 2. 프록시 객체의 멤버 변수에 직접 접근해서는 안되며, 대신에 접근자 메서드를 사용해야 한다.
 *
 * [상속관계와 프록시]
 * 정리해보면, 엔티티 상속관계에서 프록시를 부모 타입으로 조회하게 되면, 프록시와 원본 엔티티를 instanceof 비교를 수행할 수 없으며,
 * 프록시 클래스 타입과 원본 엔티티의 클래스타입이 상속관계가 아니기 때문에 다운캐스팅을 수행할 수 없다.

 * 프록시를 부모 타입으로 조회하는 문제는 글로벌 패치 설정이 지연 로딩으로 설정되어 있고, 다형성을 사용하는 로직에서 프록시를 부모로 조회하게 되어 발생한다.
 *
 * 이를 해결하기 위한 방법으로는 3가지가 존재한다.
 * 1. JPQL로 대상 직접 조회하기 -> 다만 이 방식은 자식타입을 직접 조회하기 때문에 다형성의 장점을 살릴 수 없다.
 * 2. 프록시 벗기기(unProxy)
 *    JPA에서는 unProxy라는 메서드를 지원하며, 이 메서드는 프록시 객체를 초기화하며, 원본 엔티티를 리턴해주는 메서드이다.
 *    item이라는 프록시 객체를 벗기게 되면, 부모 클래스인 Item 타입의 객체가 반환되는 것이 아니라, 프록시가 참조하는 원본 엔티티인 Book 엔티티가 반환되는 것이다.
 *    프록시 벗기기를 사용하면 프록시와 상속 문제를 해결할 수는 있지만, 영속성 컨텍스트의 동일성 문제가 추가로 발생한다. 즉 프록시 객체와 원본 엔티티를 모두 사용하기 때문에 동일성 문제가 발생할 수 있다.
 *    따라서 권장하지 않는다.
 * 3. 비지터 패턴
 *    비지터 패턴은 원본 엔티티들을 감싸는 visitor 클래스들을 생성한다.
 *
 *  public class PrintVisitor implements Visitor {
        @Override
        public void visit(Book book) {
            // 비지터에서는 프록시가 아니라 원본 엔티티를 받는다.
            System.out.println("book.class = " + book.getClass());
            System.out.println("저자: " + book.getAuthor);
        }

        @Override
        public void visit(Album album){...}
        @Override
        public void visit(Movie movie){...}

    }
 *
 * @Entity
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @DiscriminatorColumn(name = "DTYPE")
    public abstract class Item {

        @Id @GeneratedValue
        @Column(name = "ITEM_ID")
        private Long id;

        private String name;

        ...

        public abstract void accept(Visitor visitor);
    }

    @Entity
    @DiscriminatorValue("B")
    public class Book extends Item {

        private String author;
        private String isbn;


        @Override
        public void accept(Visitor visitor){
            visitor.visit(this);
        }
    }
 *
 * 이후 이처럼 visitor.visit을 호출하는 메서드를 각 엔티티에 추가한다.
 *
 *  @Test
    public void test(){

        OrderItem orderItem = em.find(OrderItem.class, orderItemId);
        Item item = orderItem.getItem();

        item.accept(new PrintVisitor());
    }
 *
 * 이렇게 되면 프록시 객체도 메서드를 호출하는 방식으로 동작하게 되고,
 * 프록시 객체가 메서드를 호출하게 되면, 원본 엔티티의 메서드를 호출하게 되고, 이 과정에서 Visitor에서는 원본 엔티티를 사용할 수 있게 된다.
 *
 * 다만 Visitor 패턴도 사용하기가 까다롭기 때문에 상황에 맞게 적절한 방법을 사용해야 한다.
 *
 * [N+1 문제와 해결 방법]
 * N+1 문제의 발생 원인
 * 1. 즉시 로딩 시에 JPQL을 활용해서 엔티티를 조회하는 경우
 * 2. 지연 로딩 시에 연관된 엔티티를 loop를 통해 하나씩 모두 접근하는 경우
 *
 * N+1 문제 해결방법
 * 1. 패치 조인 -> 연관된 엔티티를 한번에 조회(즉시 로딩)
 * 2. @Batch -> 연관된 엔티티를 조회할 때, 지정한 size만큼 SQL의 IN절을 사용해서 함께 조회한다.(@org.hibernate.annotations.BatchSize(size = 5))
 *    이 방식은 N+1 문제를 근본적으로 해결하는 방식이라기 보단, 지연 로딩 방식을 그대로 활용하면서 연관된 엔티티의 쿼리 수를 줄일 수 있다는 장점이 있다.
 * 3. 하이버네이트의 @Fetch(FetchMode.SUBSELECT)(@org.hibernate.annotations.Fetch(FetchMode.SUBSELECT))
 *    이 방식을 활용하면 엔티티를 조회하는 시점에 서브 쿼리를 통해 연관된 엔티티를 한번에 조회한다.
 *
 * 실무에서는 글로벌 패치 전략을 지연 로딩으로 설정하고, N+1 문제가 발생하는 부분에서만 패치 조인을 사용하는 것을 권장한다.
 *
 * [읽기 전용 쿼리의 성능 최적화]
 * 엔티티가 영속성 컨텍스트에서 관리되면 1차 캐시, 변경 감지 등 다양한 기능을 제공받을 수 있다.
 * 영속성 컨텍스트는 변경 감지를 위해 스냅샷 인스턴스를 보관하기 때문에 메모리를 많이 사용한다는 단점이 있다.
 * 엔티티를 단순히 조회만 하고 엔티티를 수정하지 않을 경우에는, 읽기 전용으로 엔티티를 조회하면 영속성 컨텍스트가 스냅샷 인스턴스를 보관하지 않기 때문에 메모리를 최적화 할 수 있다.
 *
 * 따라서 엔티티를 단순히 읽기만 하는 경우에는 엔티티의 스냅샷을 영속성 컨텍스트에 저장하지 않는 읽기 전용 조회를 함으로서 메모리를 최적화할 수 있다.
 *
 * 이렇게 읽기 전용으로 엔티티를 조회하는 방법으로는 두가지가 존재한다.
 * 1. 읽기 전용 쿼리 힌트 사용(query.setHint("org.hibernate.readOnly", true)) -> 스냅샷 관리 x
 * 2. 읽기 전용 트랜잭션 사용(@Transactional(readOnly = true)) -> 트랜잭션을 커밋하지 않으므로 플러시가 호출되지 않음 -> 쓰기 지연과 같은 무거운 연산 사용 x
 *
 * 따라서 엔티티를 읽기 전용으로 조회하는 경우에는 읽기 전용 쿼리 힌트와 읽기 전용 트랜잭션을 사용해서 성능을 최적화할 수 있다.
 *
 * [배치 처리]
 * 수많은 엔티티를 동일한 방식으로 수정하는 경우에는 JPQL을 활용하여 벌크 연산을 수행하면 된다.
 * 하지만 여러 엔티티를 각각 서로 다른 방식으로 수정하는 경우에는 벌크 연산으로 한번에 처리할 수 없다.
 * 따라서 이 경우에는 각 엔티티를 조회하고 하나하나 수정해야 한다.
 * 하지만 수정해야 하는 엔티티가 수만개라고 하면, 영속성 컨텍스트에서 관리하는 엔티티의 수가 너무 많아지며, 이는 메모리 부족 오류가 발생할 수 있다.
 *
 * 수많은 데이터를 처리하는 경우에는 배치 처리를 통해 대량의 데이터를 한꺼번에 모아서 처리하는 방식을 사용해야 한다.
 * 이때 배치 처리는 적절한 단위로 영속성컨텍스트를 플러시하고 초기화 함으로써, 영속성 컨텍스트에 너무 많은 엔티티가 저장되는 것을 방지해야 한다.
 *
 *  for(int i = 0; i < 100000; i++){
        Product product = new Product("item" + i, 10000);
        em.persist(product);

        //100건마다 플러시와 영속성 컨텍스트 초기화
        if (i %  100 == 0){
            em.flush();
            em.clear();
        }
    }
 *
 * 예를 들면 이 코드는 100건마다 영속성 컨텍스트 플러시 및 초기화를 수행함으로서 메모리를 관리한다.
 *
 * 또한 배치 처리를 위해서는 다음과 같은 두가지 방법을 사용할 수 있다.
 * 1. 데이터베이스의 페이징 기능을 활용하여 엔티티를 나누어서 조금씩 수정 및 플러시
 * 2. 데이터베이스의 커서 기능을 활용(하이버네이트의 scroll)하여 데이터를 순차적으로 조회
 *
 * 배치 처리는 결국 영속성 컨텍스트에 너무 많은 엔티티가 저장되어 메모리가 부족해지는 문제를 해결하는 것이 핵심이다.
 * 따라서 일정 주기마다 영속성 컨텍스트를 플러시하고 초기화하는 작업이 필요하다.
 *
 * [트랜잭션을 지원하는 쓰기 지연과 성능 최적화]
 * 네트워크 호출은 비용이 매우 큰 작업. 따라서 이를 최소화하는 것이 중요하다(N+1문제도 비슷한 이유)
 * JDBC가 제공하는 SQL 배치 기능을 사용하면, SQL을 모았다가 데이터베이스에 한번에 SQL을 보내서 네트워크 호출 횟수를 줄일 수 있다.
 * JPA에서는 트랜잭션을 지원하는 쓰기 지연과 플러시를 통해서 SQL 배치 기능을 효과적으로 사용할 수 있다
 *
 * JPA에서는 영속성 컨텍스트의 쿼리 저장소에 SQL들을 보관해두고, 트랜잭션이 커밋되어 영속성 컨텍스트가 플러시 되는 시점에 이 SQL들을 한번에 전송한다.
 * 이 방식을 통해서 네트워크 호출을 최소화하여 성능을 최적화할 수 있다.
 *
 * [쓰기 지연과 데이터베이스 락]
 * 트랜잭션을 지원하는 쓰기 지연 기능을 활용했을 때 가장 큰 장점은 데이터베이스 테이블 로우에 락이 걸리는 시간을 최소화한다는 점이다.
 * 트랜잭션을 통해 특정 데이터베이스의 데이터의 값을 수정하게 되면 그 시점부터 해당 트랜잭션이 해당 데이터에 대한 락을 얻는다.
 *
 * 만약 쓰기 지연을 하지않고 쿼리를 바로 전달한다면 트랜잭션이 그 시점부터 데이터에 대한 락을 얻으며 락을 오래 점유하여 성능이 떨어질 위험이 있다.
 * 하지만 쓰기 지연을 사용하면 트랜잭션이 커밋되고 영속성 컨텍스트가 플러시 되는 시점까지 데이터에 대한 락을 얻지 않기 때문에, 락이 걸리는 시간을 최소화할 수 있다.
 *
 * [트랜잭션의 격리 수준]
 * 트랜잭션은 ACID라 하는 원자성(Atomicity), 일관성(Consistency), 격리성(Isolation), 지속성(Durability) 를 보장해야 한다.
 * 이 중 격리성은 동시에 실행되는 트랜잭션들이 서로에게 영향을 미치지 않도록 격리하는 것이다.
 * 트랜잭션의 격리성과 동시성은 서로 반대되기 때문에 동시성을 통해 성능을 향상 시키면 트랜잭션간의 격리성을 완벽하게 보장할 수 없으며, 격리성을 완벽하게 보장하면, 트랜잭션의 동시성 처리 성능이 나빠지게 된다.
 * 즉 트랜잭션의 격리성과 동시성은 서로 trade-off 관계에 있다.
 *
 * 이때문에 ANSI 표준에서는 트랜잭션의 격리 수준을 4단계로 나누고, 개발자가 원하는 수준을 설정할 수 있게 지원한다.
 *
 * READ UNCOMMITED (커밋되지 않은 읽기)
 * READ COMMITTED (커밋된 읽기)
 * REPEATABLE READ (반복 가능한 읽기)
 * SERIALIZABLE (직렬화 기능)
 *
 * 이중 READ UNCOMMITED는 가장 격리성이 낮으며, 동시성이 증가한다.
 * 이후 아래로 내려올 수록 격리성이 증가하며, 동시성은 낮아진다.
 *
 * READ UNCOMMITTED: 커밋되지 않은 읽기는 서로 다른 트랜잭션에서 아직 커밋되지 않은 데이터를 조회하고 수정할 수 있다. 이것을 DIRTY READ 라고 한다.
 * 커밋되지 않은 읽기를 사용하면 동시성은 크게 증가하지만, DIRTY READ에 의해 데이터 정합성에 심각한 문제가 발생할 수 있다.
 *
 * READ COMMITTED: 커밋된 읽기는 말그대로 커밋한 데이터만 읽을 수 있다. 따라서 위에서 언급한 DIRTY READ가 발생하지 않는다.
 * NON-REPEATABLE READ 는 발생할 수 있다. NON-REPEATABLE READ란, 데이터를 반복하여 조회할 때마다, 조회 결과가 달라질 수 있다는 의미이다.
 * NON-REPEATABLE READ는 데이터를 반복해서 조회할 때, 그사이에 다른 트랜잭션에서 엔티티를 수정하는 경우에 발생한다.
 * 따라서 커밋된 읽기를 사용하면 DIRTY READ 문제가 발생하지 않아 데이터 정합성 문제가 발생하지 않지만, 비즈니스 로직에서 조회하는 엔티티의 값이 달라질 수 있다는 문제점은 남아있다.
 *
 * REPEATABLE READ: 반복 가능한 읽기는 말 그대로 NON-REPEATABLE READ 문제가 발생하지 않는다는 것이다. 즉 트랜잭션 내에서 한번 조회한 데이터를 반복해서 조회해도 같은 데이터가 조회된다.
 * 하지만 반복 가능한 읽기에서도 PHANTOM READ 문제는 발생할 수 있다. 이는 반복 조회시 결과 집합이 달라지는 것을 의미한다.
 * 반복 가능한 읽기에서는 DIRTY READ와 NON-REPEATABLE READ 문제는 발생하지 않지만, PHANTOM READ는 발생할 수 있다.
 *
 * SERIALIZABLE: 수준은 PHANTOM READ 문제도 발생하지 않지만, 트랜잭션간의 동시성 성능이 매우 떨어지기 때문에 성능상 추천하지 않는다. -> 사실상 순차적인 데이터 접근
 *
 * 에플리케이션에서는 동시성 처리 성능이 중요하기 때문에 기본적으로는 READ COMMITED 격리 수준을 기본으로 제공한다.
 * 만약 비즈니스 로직 상 더 높은 격리 수준이 필요할 경우에는 JPA와 데이터베이스의 트랜잭션이 제공하는 락 기능을 활용할 수 있다.
 *
 * [락 기능을 통한 격리 수준 높이기]
 * JPA와 데이터베이스 트랜잭션이 제공하는 락 기능을 활용하면 격리 수준을 더 높일 수 있다.
 * JPA를 사용할 때에는 낙관적 락, 비관적 락 두가지중에 한가지를 선택해서 사용할 수 있다.
 *
 * 낙관적 락은 트랜잭션 대부분이 충돌이 발생하지 않는다고 낙관적으로 가정하는 방법이다.
 * 낙관적 락은 데이터베이스가 제공하는 락 기능을 사용하는 것이 아니라, JPA가 제공하는 버전 관리 기능을 사용한다.
 * 낙관적 락을 사용하면 트랜잭션을 커밋하는 시점까지는 충돌을 알 수 없다는 단점이 있으며, 충돌이 발생하면 예외 처리를 해줘야 한다.
 * 하지만 낙관적 락을 사용하면, 데이터베이스의 락이 걸리지 않기 때문에 동시성 성능이 향상된다.
 *
 * 비관적 락은 트랜잭션의 충돌이 발생한다고 가정하고 우선 락을 걸고 보는 방법이다.
 * 이 방법은 데이터베이스가 제공하는 락 기능을 사용한다.
 * 비관적 락을 사용하면 데이터를 수정하는 시점에 한 트랜잭션이 락을 획득하고, 다른 트랜잭션들은 락 획득을 대기하기 때문에 충돌 자체를 방지할 수는 있지만, 다른 트랜잭션들이 락을 획득하기 위해 대기해야 하기 때문에 동시성 성능이 떨어진다는 단점이 있다.
 *
 * 낙관적 락과 비관적 락을 사용하면 트랜잭션의 격리 수준을 높일 수 있으며, 추가적으로 두 번의 갱신 분실 문제도 해결할 수 있다
 *
 * 두 번의 갱신 분실 문제: 두 트랜잭션이 동일한 데이터에 대해서 동시에 수정하고 순차적으로 커밋한 경우, 나중에 커밋한 변경사항만 반영되고, 첫번째 변경사항은 무시되는 문제
 *
 * [낙관적 락]
 * 낙관적 락은 JPA가 제공하는 버전 관리 기능을 사용한다.
 * JPA가 제공하는 버전관리 기능은 @Version 에노테이션을 사용한다.
 *
 *  @Entity
    public class Board{

        @Id
        private String id;
        private String title;

        @Version
        private Integer version;
    }
 *
 * @Version 에노테이션을 버전 관리용 필드에 설정하면, JPA가 자동으로 해당 엔티티에 대하여 버전 관리를 수행해준다.
 * 이렇게 @Version을 통해서 버전 관리용 필드를 추가하게 되면,엔티티를 수정하고 트랜잭션을 커밋하여 변경사항을 데이터베이스에 반영할 때마다 버전이 하나씩 자동으로 증가한다.
 * 그리고, 트랜잭션을 커밋하여 수정사항을 반영할 때, 조회 시점의 버전과 수정사항을 반영하는 시점의 버전을 비교하여, 버전이 일치하는 경우에는 수정사항을 반영하고 버전을 증가시킨다.
 * 반면에 버전이 일치하지 않는 경우에는 다른 트랜잭션에서 이미 해당 엔티티를 수정했다고 판단하고 예외를 발생시킨다.
 * 따라서 이 경우에는 예외를 처리해줘야 한다.
 *
 * 연관관계의 필드는 외래 키를 관리하는 연관관계의 주인 필드를 수정할 때에만 버전이 증가한다. 따라서 비주인이 연관관계 필드를 수정한다고 하더라도, 버전이 증가하지 않는다.
 * 즉 연관관계의 비주인이 연관관계 필드를 수정한다고 해도 Version이 증가하지 않으며, 연관관계의 주인이 연관관계 필드를 수정한 경우에만 Version이 증가한다.
 *
 * [낙관적 락과 비관적 락]
 * JPA가 제공하는 낙관적 락은 JPA의 버전 관리 기능을 기반으로 동작한다.
 * 낙관적 락을 사용하기 위해서는 엔티티에 @Version을 통한 버전 관리 필드가 설정되어 있어야 한다.
 *
 * JPA가 제공하는 비관적 락은 데이터베이스 트랜잭션 락 메커니즘에 의존하는 방법이다.
 * 비관적 락은 버전 정보를 사용하지 않는다. 비관적 락은 주로 PESSIMISTIC_WRITE 모드를 사용한다.
 *
 * PESSIMISTIC_WRITE: 데이터베이스에 쓰기 락을 건다.
 * PERSSIMISTIC_READ: 데이터베이스에 읽기 락을 건다.
 *
 * [비관적 락과 타임아웃]
 * 비관적락은 한 트랜잭션이 엔티티에 대해서 락을 얻고, 다른 트랜잭션이 해당 엔티티에 접근하는 것을 막는다.
 * 하지만 한 트랜잭션이 락을 너무 오래 점유하고 있으면 다른 트랜잭션은 계속 대기해야 하며 이는 성능상 문제가 발생할 수 있다.
 * 따라서 비관적 락을 사용할 때에는 타임아웃 시간을 설정하고, 시간이 지나면 예외가 발생한다.
 *
 * [트랜잭션 격리 수준과 락 사용 전략]
 * 실제 애플래케이션을 개발할 때에는 트랜잭션의 격리 수준을 적절하게 설정하여, 데이터 무결성도 어느정도 보장하며, 동시성 성능도 챙기는 것이 중요하다.
 * 따라서 트랜잭션 격리 수준은 기본값인 READ COMMITED 격리 수준을 사용하고, 격리 수준을 높여야 하는 경우에는 락을 사용하여 격리 수준을 높여야 한다.
 * 또한 동시성 성능은 낙관적 락이 비관적 락보다 좋기 때문에 낙관적 락을 사용하는 것을 권장한다.
 *
 * [캐시]
 * DB에 접근하여 데이터를 load하는 것은 비용이 비싸다.
 * 따라서 자주 접근되는 데이터는 메모리에 캐시해서 네트워크 비용을 줄이는 것이 중요하다.
 *
 * 하지만 실제로는 JPA에서 제공하는 2차 캐시보다 Redis를 더 많이 사용한다.
 *
 * 영속성 컨텍스트의 1차 캐시를 활용하면 DB 접근 횟수를 줄여 성능을 향상시킬수는 있지만, 영속성 컨텍스트는 생존주기가 너무 짧아 데이터가 너무 빨리 사라진다.
 * 따라서 영속성 컨텍스트 범위의 캐시가 아니라 애플리케이션 범위의 캐시를 지원해야 하고, 이것을 2차 캐시라고 한다.
 *
 * 영속성 컨텍스트의 1차 캐시와 2차 캐시가 모두 존재할때 동작과정은 다음과 같다.
 * 1. 엔티티 매니저를 통해 엔티티를 조회할 때, 1차 캐시에 엔티티가 저장되어 있으면 1차 캐시에서 엔티티를 조회하여 반환한다. 만약 1차 캐시에 저장되어 있지 않으면 2차 캐시를 조회한다.
 * 2. 만약 2차 캐시에 엔티티가 없으면 데이터베이스를 조회한다.
 * 3. 데이터베이스에서 엔티티를 조회한 결과를 2차 캐시에 보관한다.
 * 4. 2차 캐시는 자신이 보관한 엔티티를 복사해서 영속성 컨텍스트의 1차 캐시에 반환한다.(복사본 반환)
 * 5. 1차 캐시에서는 2차 캐시로부터 반환받은 엔티티를 저장하고 보관한다.
 * 6. 이후에 다시 엔티티 매니저를 통해 엔티티를 조회할 경우, 이제 1차 캐시에 엔티티에 저장되어 있기 때문에 1차 캐시에서 엔티티를 조회하여 바로 반환한다
 *
 * 1차 캐시에서는 1차 캐시에 캐시한 객체를 직접 반환한다.
 * 2차 캐시에서는 동시성을 극대화하기 위해 캐시한 객체를 직접 반환하지 않고 복사본을 만들어서 반환한다.
 * 만약 2차 캐시에서 복사본이 아니라 객체를 직접 반환한다면, 여러 영속성컨텍스트에서 해당 객체를 받아서 수정할 때, 데이터 정합성에 문제가 발생할 수 있고, 이때문에 락을 설정해야 한다.
 * 따라서 이렇게 되면 동시성 성능이 떨어지게 된다.
 * 따라서 2차 캐시에서는 객체를 직접 반환하는 것이 아니라 복사본을 만들어서 반환한다.
 *
 * 이러한 특징때문애 2차 캐시를 사용하면 1차 캐시간의 엔티티의 동등성은 보장하지만 동일성은 보장하지 않으며, 1차 캐시에서는 엔티티의 동일성을 보장하게 된다.
 *
 * [JPA 2차 캐시 기능]
 *
 *  @Cacheable
    @Entity
    public class Member{

        @Id @GeneratedValue
        private Long id;
        ...
    }
 *
 * JPA에서는 @Cacheable 에노테이션을 통해서 2차 캐시 기능을 제공한다.
 *
 * 이후 설정파일에서 shared-cache-mode를 설정해서 2차 캐시 방식을 설정한다.
 * ALL: 모든 엔티티를 캐시한다.
 * NONE: 캐시를 사용하지 않는다.
 * ENABLE_SELECTIVE: Cacheable(true)로 설정된 엔티티만 캐시를 적용한다.
 * DISABLE_SELECTIVE: 모든 엔티티를 캐시하는데 @Cacheable(false)로 명시된 엔티티는 캐시하지 않는다.
 *
 *
 * 또한 JPA에서는 2차 캐시를 직접 운영할 수 있도록 javax.persistence.Cache 인터페이스를 제공한다.
 * Cache 인터페이스는 EntityManagerFactory에서 구할 수 있다.
 * Cache cache = emf.getCache();
 *
 *  public interface Cache {

        //해당 엔티티가 캐시에 있는지 여부 확인
        public boolean contains(Class cls, Object primaryKey);

        //해당 엔티티중 특정 식별자를 가진 엔티티를 캐시에서 제거
        public void evict(Class cls, Object primaryKey);

        //해당 엔티티 전체를 캐시에서 제거
        public void evict(Class cls);

        // 모든 캐시 데이터 제거
        public void evictAll();

        // JPA Cache 구현체 조회
        public <T> unwrap(Class<T> cls);
    }
 *
 * Cache 인터페이스는 다음과 같은 메서드를 제공하며, 이를 통해 개발자가 세밀하게 2차 캐시를 제어할 수 있다.
 *
 * [JPA 구현체의 2차 캐시]
 * EHCACHE는 자바 기반의 캐시 라이브러리이며, JPA의 2차 캐시 구현체로 가장 많이 사용되는 캐시 엔진이다.
 *
 * 하이버네이트가 지원하는 캐시는 크게 3가지가 있다.
 * 엔티티 캐시: 엔티티 단위로 캐시한다. 식별자로 엔티티를 조회하거나 컬렉션이 아닌 연관된 엔티티를 로딩할 때 사용한다.
 * 컬렉션 캐시: 엔티티와 연관된 컬렉션을 캐시한다. 만약 컬렉션이 엔티티를 담고 있으면 식별자 값만 캐시한다.
 * 쿼리 캐시: 쿼리와 파라미터 정보를 키로 사용해서 캐시한다. 결과가 엔티티면 식별자 값만 캐시한다.
 * 이때 식별자 값만 캐시하는 이유는 메모리를 절약하기 위해서이다.
 *
 * 2차 캐시는 애플리케이션 범위의 캐시이기 때문에, 저장할 데이터의 수를 제한하지 않거나, 생존 주기를 설정하지 않으면, 2차 캐시에 데이터가 계속 쌓이며 메모리 부족문제가 발생할 수 있다.

 * 따라서 2차 캐시를 사용할 때에는 꼭 최대 데이터 수와, 2차 캐시 생존주기를 설정하여 주기적으로 2차 캐시를 초기화해야 한다.
 *
 * 엔티티를 캐시하려면 @Cacheable 에노테이션을 적용하면 된다.
 * 또한 @Cache 에노테이션을 사용해서 더 세밀한 캐시 설정을 할 수 있다.
 *
 * EHCACHE는 데이터를 수정하면 캐시 데이터도 함께 수정된다.
 *
 * 2차 캐시는 각각의 캐시마다 캐시 영역을 나누어서 저장한다.
 * 엔티티 캐시 영역: cache.ParentMember
 * 컬렉션 캐시 영역: cache.ParentMember.childMembers
 *
 * 이후 설정파일에서 각각의 캐시 영역별로 세부적인 설정이 가능하다.
 *
 * em.createQuery("select i from Item i", Item.class)
	.setHint("org.hibernate.cacheable", true)
    .getResultList();
 *
 * 쿼리 캐시의 경우에는 다음과 같이 org.hibernate.cacheable이라는 쿼리 힌트를 true로 설정해주면 된다.
 * 쿼리 캐시 영역은 두가지로 구성된다.
 * org.hibernate.cache.internal.StandardQueryCache: 쿼리 캐시를 저장하는 영역이다. 이곳에는 쿼리, 쿼리 결과 집합, 쿼리를 실행한 시점의 타임스탬프를 보관한다.
 * org.hibernate.cache.spi.UpdateTimestampsCache: 쿼리 캐시가 유효한지 확인하기 위해 쿼리 대상 테이블의 가장 최근 변경 시간을 저장하는 영역이다.
 *
 * 만약 standardQueryCache 캐시 영역의 타임스탬프가 더 오래 되었다면, 마지막으로 쿼리가 캐시된 이후, 엔티티의 값이 변경되었다는 의미이기 때문에, 캐시가 유효하지 않다고 판단하여 데이터베이스에서 데이터를 조회해서 다시 캐시한다.
 *
 * 즉 쿼리 캐시는 데이터 정합성을 유지하기 위해서 다음과 같이 두가지의 캐시 영역을 관리한다.
 * 따라서 쿼리 캐시는 수정이 거의 일어나지 않는 테이블에 사용해야 효과를 볼 수 있다.
 *
 * 만약 쿼리 캐시나 컬렉션 캐시만 사용하고 대상 엔티티에 엔티티 캐시를 적용하지 않으면 성능상 매우 심각한 문제가 발생할 수 있다.
 *
 * 쿼리 캐시나 컬렉션 캐시에서는 엔티티의 식별자 값만 저장하고, 이를 반환하는데, 만약 엔티티 캐시에 해당 엔티티 정보가 없다면 매번 DB에서 해당 식별자 값을 기반으로 엔티티를 조회할 것이다.
 * 이러면 결국 2차 캐시를 사용하는 의미가 사라진다.
 * 따라서 쿼리 캐시나 컬렉션 캐시를 사용하는 경우에는 해당 엔티티가 꼭 엔티티 캐시에 저장되어 있어야 한다.
 *
 * JPA 2차 캐시는 ‘ORM 내부 최적화 도구’이고, Redis는 ‘시스템 전체를 위한 범용 인프라’이다.
 * 즉 JPA는 객체를 캐시하는 것에만 초점을 맞추지만, Redis는 DTO, 응답 JSON, 집계 결과, 검색 결과, 추천 결과 등 범용적으로 캐싱
 * 따라서 실무에서는 JPA의 2차 캐시보다는 Redis를 더 많이 사용한다.
 *
 * JPA 2차 캐시: "엔티티 객체를 재사용"
 * Redis 캐시: "최종 계산 결과 자체를 재사용"
 *
 * DTO: Data Transfer Object(데이터 전달 객체)
 * DTO를 사용해서 엔티티를 그대로 전달하는 것이 아니라, 꼭 필요한 필드만 노출시켜서 데이터를 전달하는 것이 좋다.
 *
 * DTO의 장점
 * 1. 보안 문제: 중요한 정보를 숨김으로서 보안을 강화할 수 있다.
 * 2. API 스펙이 DB 구조에 종속되지 않음. 즉 API 스펙이 DTO에 종속되며, DB 구조와는 무관해진다.
 * 3. 서비스 계층에서 컨트롤러 계층으로 엔티티를 전달하는 것이 아니라 DTO를 전달함으로서 준영속상태에서의 지연로딩 문제가 발생하지 않는다.(DTO 객체를 생성하는 과정에서 프록시 객체 초기화가 발생한다.)
 *
 * */

public class Summary {
}
