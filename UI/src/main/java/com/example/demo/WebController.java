package com.example.demo;
import io.minio.MinioClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.UUID;


@Controller
public class WebController implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/results").setViewName("results");
	}
//
//	@PostMapping("/home")
//	public String mainSubmit(@ModelAttribute InputNode inputNode, Model model) {
//		model.addAttribute("inputNode", inputNode);
//		return "results";
//	}

	@GetMapping("/home")
	public String home(@ModelAttribute InputNode inputNode, Model model) {
//		model.addAttribute("inputNode", inputNode);
		return "home";
	}

	@RequestMapping(value = "/home", method = RequestMethod.POST)
	public String home(@RequestParam(value = "userInput", required = false) String userInput,
					   @RequestParam(value = "userOperation", required = false) String userOperation,
					   Model model) {
		model.addAttribute("userInput",userInput);
		model.addAttribute("userOperation", userOperation);

		String ENDPOINT = "http://127.0.0.1:9000";
		String ACCESSKEY = "minioadmin";
		String SECRETKEY = "minioadmin";
		String currentPendingOperation = UUID.randomUUID().toString();

		MinioClient minioClient = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESSKEY, SECRETKEY).build();
		try {
			String result = InputNode.inputSelection(minioClient, userOperation, userInput, currentPendingOperation);
			System.out.println("RESULT: " + result);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "results";
	}
	@ModelAttribute("userOperation")
	public String[] getuserOperation() {
		return new String[]{
				"reverseString", "toUpperCase"
		};
	}

}
